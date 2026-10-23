# -*- coding: utf-8 -*-
"""
ADI Finetune Worker
===================
aideepin 微调训练 Worker：
- --mock  模拟训练全流程（无 GPU 也可跑通：领取任务 -> 下载集 -> 模拟进度/日志 -> 产物回传）
- --real  调用 LLaMA-Factory 真实训练（需 GPU 机器，见 README.md 的部署说明）

协议（/ext/v1/finetune/*，Authorization: ext-xxx 用户级 API Key）：
  POST /worker/heartbeat                {workerId, gpuInfo}
  POST /job/claim                       {workerId}            -> WorkerClaimResp | null
  GET  /dataset/{fileUuid}/download     -> jsonl 文件流
  POST /job/{uuid}/progress?workerEpoch {progress,currentEpoch,trainLoss,evalLoss}
                                        -> {accepted, stopRequested}
  POST /job/{uuid}/log?workerEpoch      {content}
  POST /job/{uuid}/complete?workerEpoch multipart: file? + artifactType/name/externalPath/...
  POST /job/{uuid}/fail?workerEpoch     {reason}

配置来源（环境变量优先，其次 config.json）：
  ADI_SERVER_URL  后端地址，如 http://127.0.0.1:9999
  ADI_API_KEY     训练 Worker Key（管理端 /admin/finetune/workerKey/generate 生成，ext- 开头）
  ADI_WORKER_ID   Worker 标识，缺省 主机名-pid
"""
import argparse
import json
import math
import os
import random
import socket
import sys
import time
import zipfile
from pathlib import Path

import requests

WORK_DIR = Path(__file__).resolve().parent
DATA_DIR = WORK_DIR / "workspace"


def load_config():
    cfg_path = WORK_DIR / "config.json"
    cfg = {}
    if cfg_path.exists():
        cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    cfg["server_url"] = os.environ.get("ADI_SERVER_URL", cfg.get("server_url", "http://127.0.0.1:9999")).rstrip("/")
    cfg["api_key"] = os.environ.get("ADI_API_KEY", cfg.get("api_key", ""))
    cfg["worker_id"] = os.environ.get("ADI_WORKER_ID", cfg.get("worker_id", f"{socket.gethostname()}-{os.getpid()}"))
    cfg["poll_interval"] = int(os.environ.get("ADI_POLL_INTERVAL", cfg.get("poll_interval", 10)))
    if not cfg["api_key"]:
        print("[FATAL] missing api key: set ADI_API_KEY or config.json api_key "
              "(generate via admin page /admin/finetune/workerKey/generate)")
        sys.exit(1)
    return cfg


class Client:
    def __init__(self, cfg):
        self.cfg = cfg
        self.session = requests.Session()
        self.session.headers["Authorization"] = cfg["api_key"]

    def url(self, path):
        return self.cfg["server_url"] + "/ext/v1/finetune" + path

    @staticmethod
    def _unwrap(resp):
        """服务端有全局响应包装 {success, code, message, data?}；data 为 null 时键可能缺省"""
        body = resp.json()
        if isinstance(body, dict) and ("success" in body or "code" in body):
            if body.get("success") is False:
                raise RuntimeError(f"server error: {body.get('message')}")
            return body.get("data")
        return body

    def heartbeat(self, gpu_info=""):
        r = self.session.post(self.url("/worker/heartbeat"),
                              json={"workerId": self.cfg["worker_id"], "gpuInfo": gpu_info}, timeout=15)
        r.raise_for_status()

    def claim(self):
        r = self.session.post(self.url("/job/claim"), json={"workerId": self.cfg["worker_id"]}, timeout=15)
        r.raise_for_status()
        return self._unwrap(r) or None

    def download_dataset(self, file_uuid, target: Path):
        r = self.session.get(self.url(f"/dataset/{file_uuid}/download"), timeout=120)
        r.raise_for_status()
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(r.content)
        return target

    def progress(self, job_uuid, epoch, progress, current_epoch, train_loss, eval_loss):
        r = self.session.post(self.url(f"/job/{job_uuid}/progress"),
                              params={"workerEpoch": epoch},
                              json={"progress": round(progress, 2), "currentEpoch": current_epoch,
                                    "trainLoss": round(train_loss, 4) if train_loss is not None else None,
                                    "evalLoss": round(eval_loss, 4) if eval_loss is not None else None},
                              timeout=15)
        r.raise_for_status()
        return self._unwrap(r)

    def log(self, job_uuid, epoch, content):
        try:
            self.session.post(self.url(f"/job/{job_uuid}/log"), params={"workerEpoch": epoch},
                              json={"content": content}, timeout=15)
        except requests.RequestException as e:
            print(f"[warn] log push failed: {e}")

    def complete(self, job_uuid, epoch, adapter_zip: Path = None, artifact_type=None,
                 name=None, external_path=None, external_size=None, remark=None, metrics=None):
        data = {"artifactType": artifact_type or "", "name": name or "",
                "externalPath": external_path or "", "remark": remark or ""}
        if external_size:
            data["externalSizeBytes"] = str(external_size)
        if metrics:
            # 训练报告随终态一起上报（服务端落 adi_finetune_job.metrics）
            data["metrics"] = json.dumps(metrics, ensure_ascii=False)
        files = {}
        if adapter_zip:
            files["file"] = (adapter_zip.name, open(adapter_zip, "rb"), "application/zip")
        r = self.session.post(self.url(f"/job/{job_uuid}/complete"), params={"workerEpoch": epoch},
                              data=data, files=files or None, timeout=600)
        r.raise_for_status()
        return self._unwrap(r)

    def finish(self, job_uuid, epoch, status, reason):
        """终态上报：status ∈ {failed, stopped}（success 走 complete）"""
        try:
            r = self.session.post(self.url(f"/job/{job_uuid}/finish"),
                                  params={"workerEpoch": epoch, "status": status},
                                  json={"reason": (reason or "")[:500]}, timeout=15)
            r.raise_for_status()
        except requests.RequestException as e:
            print(f"[warn] finish report failed: {e}")


# ---------------------------------------------------------------- mock mode

def make_fake_adapter_zip(target: Path, job_name: str):
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("adapter_config.json", json.dumps({
            "peft_type": "LORA", "r": 8, "lora_alpha": 16, "lora_dropout": 0.05,
            "note": f"mock adapter generated for {job_name}"}, ensure_ascii=False, indent=1))
        zf.writestr("adapter_model.safetensors", b"MOCK-BINARY-" + os.urandom(64))
        zf.writestr("README.md", f"# mock adapter\njob: {job_name}\n")
    return target


def run_mock_job(client: Client, job: dict):
    job_uuid = job["jobUuid"]
    epoch = job["workerEpoch"]
    hyper = job.get("hyperParams") or {}
    num_epochs = int(hyper.get("num_epochs", 3))
    lr = float(hyper.get("learning_rate", 5e-4))
    job_name = job.get("jobName") or job_uuid

    print(f"[mock] claimed job {job_uuid} ({job_name}), base={job.get('baseModelName')}, epochs={num_epochs}")
    client.log(job_uuid, epoch, f"[mock] job claimed: {job_name}\n[mock] hyper: {json.dumps(hyper)}")

    # 下载数据集并统计行数
    ds_file = DATA_DIR / job_uuid / "dataset.jsonl"
    if job.get("datasetFileUuid"):
        client.download_dataset(job["datasetFileUuid"], ds_file)
        n_lines = sum(1 for l in ds_file.read_text(encoding="utf-8").splitlines() if l.strip())
        client.log(job_uuid, epoch, f"[mock] dataset downloaded, samples={n_lines}")
    else:
        n_lines = 100

    # 模拟训练：loss 按指数衰减 + 噪声；总步数按 2 秒/步模拟
    total_steps = max(10, n_lines // 8)
    base_loss = 2.2
    eval_loss = None
    first_train_loss = None
    best_eval_loss = None
    eval_history = []
    start = time.time()
    for step in range(1, total_steps + 1):
        time.sleep(0.5 if os.environ.get("ADI_MOCK_FAST") else 2.0)
        train_loss = base_loss * math.exp(-3.0 * step / total_steps) + random.uniform(0.02, 0.12)
        if first_train_loss is None:
            first_train_loss = train_loss
        if step % max(1, total_steps // (num_epochs * 2)) == 0:
            eval_loss = train_loss + random.uniform(0.05, 0.2)
            eval_history.append(eval_loss)
            best_eval_loss = min(eval_history)
        progress = 100.0 * step / total_steps
        current_epoch = min(num_epochs, max(1, math.ceil(num_epochs * step / total_steps)))
        resp = client.progress(job_uuid, epoch, progress, current_epoch, train_loss, eval_loss)
        if not resp.get("accepted"):
            print("[mock] progress rejected (row taken over), exiting")
            return
        if resp.get("stopRequested"):
            client.log(job_uuid, epoch, "[mock] stop requested by admin, aborting")
            client.finish(job_uuid, epoch, "stopped", "stopped by admin")
            return
        if step % 5 == 0 or step == total_steps:
            elapsed = time.time() - start
            client.log(job_uuid, epoch,
                       f"[mock] step {step}/{total_steps} epoch {current_epoch}/{num_epochs} "
                       f"loss {train_loss:.4f}" + (f" eval {eval_loss:.4f}" if eval_loss else "")
                       + f" elapsed {elapsed:.0f}s")

    # 产物：伪 adapter zip（上传）+ 伪 GGUF 路径（登记）+ 训练报告（metrics）
    adapter_zip = make_fake_adapter_zip(DATA_DIR / job_uuid / "adapter.zip", job_name)
    metrics = {
        "base_model": job.get("baseModelName"), "method": job.get("method"),
        "preset": hyper.get("preset", ""), "gpu": "mock-no-gpu",
        "lora_rank": hyper.get("lora_rank"), "lora_alpha": hyper.get("lora_alpha"),
        "learning_rate": lr, "cutoff_len": hyper.get("cutoff_len"),
        "num_epochs": num_epochs, "batch_size": hyper.get("batch_size"),
        "gradient_accumulation": hyper.get("gradient_accumulation"),
        "trainable_params": 8126464, "total_params": 7615616512,
        "first_train_loss": round(first_train_loss, 4) if first_train_loss else None,
        "train_loss": round(train_loss, 4),
        "eval_loss": round(eval_loss, 4) if eval_loss is not None else None,
        "best_eval_loss": round(best_eval_loss, 4) if best_eval_loss is not None else None,
        "eval_loss_increased": bool(len(eval_history) >= 2 and eval_history[-1] > best_eval_loss + 1e-4),
        "total_steps": total_steps, "epochs_done": num_epochs,
        "train_runtime": round(time.time() - start, 2),
        "best_checkpoint": f"checkpoint-{max(1, total_steps // 5)}",
        "mock": True,
    }
    client.log(job_uuid, epoch, "[mock] training done, uploading artifacts + report")
    client.complete(job_uuid, epoch,
                    adapter_zip=adapter_zip,
                    artifact_type="gguf",
                    name=f"{job_name}-mock",
                    external_path=str(DATA_DIR / job_uuid / "mock-model-q4.gguf"),
                    external_size=123456789,
                    remark="mock artifact, generated without GPU",
                    metrics=metrics)
    client.log(job_uuid, epoch, "[mock] job completed")
    print(f"[mock] job {job_uuid} completed")


# ---------------------------------------------------------------- real mode

def run_real_job(client: Client, job: dict):
    """LLaMA-Factory 真实训练（GPU 机器）。实现见 real_trainer.py（需要 GPU 环境，
    本文件保持可导入不报错：无 GPU 环境下直接报错退出）。"""
    try:
        from real_trainer import train_job
    except ImportError as e:
        print(f"[FATAL] real mode requires real_trainer.py deps (llamafactory on GPU host): {e}")
        sys.exit(1)
    train_job(client, job, DATA_DIR)


# ---------------------------------------------------------------- main loop

def main():
    parser = argparse.ArgumentParser(description="ADI finetune worker")
    parser.add_argument("--mode", choices=["mock", "real"], default="mock", help="mock: no GPU needed; real: LLaMA-Factory")
    parser.add_argument("--once", action="store_true", help="claim at most one job then exit")
    args = parser.parse_args()

    cfg = load_config()
    client = Client(cfg)
    print(f"[worker] id={cfg['worker_id']} mode={args.mode} server={cfg['server_url']}")
    gpu_info = "mock-no-gpu"
    if args.mode == "real":
        try:
            import torch
            gpu_info = torch.cuda.get_device_name(0) if torch.cuda.is_available() else "cuda-unavailable"
        except ImportError:
            gpu_info = "torch-missing"

    while True:
        try:
            client.heartbeat(gpu_info)
            job = client.claim()
            if job:
                try:
                    if args.mode == "mock":
                        run_mock_job(client, job)
                    else:
                        run_real_job(client, job)
                except KeyboardInterrupt:
                    raise
                except Exception as e:
                    print(f"[error] job {job.get('jobUuid')} failed: {e}")
                    client.finish(job.get("jobUuid", ""), job.get("workerEpoch", ""),
                                  "failed", f"{type(e).__name__}: {e}")
            else:
                print(f"[worker] no pending job, sleeping {cfg['poll_interval']}s")
        except requests.RequestException as e:
            print(f"[warn] server unreachable: {e}")
        if args.once:
            break
        time.sleep(cfg["poll_interval"])


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[worker] bye")
