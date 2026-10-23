# -*- coding: utf-8 -*-
"""
真实训练器：LLaMA-Factory 驱动的 LoRA/QLoRA 训练（GPU 机器专用）。

流程：下载数据集 -> 注册 LLaMA-Factory 数据集(dataset_info.json) -> 渲染训练 YAML
   -> llamafactory-cli train（子进程，解析进度/loss 回传） -> 按开关导出（合并 / GGUF 量化）
   -> 回传 adapter zip + 训练报告 metrics。

超参全部来自服务端 hyper_params（已在服务端填好默认值并派生 save/eval/logging 步数），
本文件只做「翻译成 LLaMA-Factory YAML」，不再自行决定默认值——避免两边默认值打架。

注意：本模块 import 期不依赖 llamafactory，只有 train_job 执行时才校验环境。
"""
import json
import math
import re
import shutil
import subprocess
import time
import zipfile
from pathlib import Path

import yaml  # pyyaml, llamafactory 安装后自带


def _log(client, job_uuid, epoch, msg):
    print(f"[trainer] {msg}")
    client.log(job_uuid, epoch, msg)


def _progress(client, job_uuid, epoch, pct, cur_epoch, loss, eval_loss):
    resp = client.progress(job_uuid, epoch, pct, cur_epoch, loss, eval_loss)
    if not resp.get("accepted"):
        raise RuntimeError("job row taken over (stale worker)")
    return resp.get("stopRequested", False)


def _int(hyper, key, default):
    v = hyper.get(key, default)
    try:
        return int(v)
    except (TypeError, ValueError):
        return default


def _float(hyper, key, default):
    v = hyper.get(key, default)
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def _bool(hyper, key, default):
    v = hyper.get(key, default)
    if v is None:
        return default
    return bool(v)


def _resolve_template(hyper, base_model_name):
    """对话模板：服务端给的名字可能不被当前 LLaMA-Factory 版本认识（模板名随版本增删），
    因此先校验 TEMPLATES 表，不认识就回退到按模型名自动识别（YAML 里不写 template）。
    这一步能救回「升级 LLaMA-Factory 后老任务全部报 template not found」的情况。"""
    wanted = (hyper.get("template") or "").strip()
    if not wanted:
        wanted = "qwen" if "qwen" in (base_model_name or "").lower() else ""
    if not wanted:
        return None
    known = None
    for mod_path in ("llamafactory.data.template", "llamafactory.extras.constants"):
        try:
            mod = __import__(mod_path, fromlist=["TEMPLATES"])
            templates = getattr(mod, "TEMPLATES", None)
            if isinstance(templates, dict):
                known = set(templates.keys())
                break
        except Exception:
            continue
    if known is not None and wanted not in known:
        print(f"[trainer][warn] template '{wanted}' not in this LLaMA-Factory build -> auto detect")
        return None
    return wanted


def _render_train_yaml(job: dict, out_dir: Path, ds_key: str, template) -> Path:
    hyper = job.get("hyperParams") or {}
    method = (job.get("method") or "lora").lower()
    epochs = _int(hyper, "num_epochs", 3)
    val_ratio = _float(hyper, "val_ratio", 0.05)
    eval_steps = _int(hyper, "eval_steps", 0)
    save_steps = _int(hyper, "save_steps", 0)
    patience = _int(hyper, "early_stopping_patience", 0)
    do_eval = val_ratio > 0 and eval_steps > 0

    yml = {
        "model": {
            "model_name_or_path": job.get("baseModelName"),
            "trust_remote_code": True,
        },
        "method": {
            "stage": "sft",
            "do_train": True,
            "finetuning_type": "lora",
            "lora_target": hyper.get("lora_target", "all"),
            "lora_rank": _int(hyper, "lora_rank", 16),
            "lora_alpha": _int(hyper, "lora_alpha", 32),
            "lora_dropout": _float(hyper, "lora_dropout", 0.05),
            "use_rslora": _bool(hyper, "use_rslora", False),
            "use_dora": _bool(hyper, "use_dora", False),
        },
        "dataset": {
            "dataset": ds_key,
            "cutoff_len": _int(hyper, "cutoff_len", 2048),
            "overwrite_cache": True,
            "preprocessing_num_workers": 8,
            "packing": _bool(hyper, "packing", False),
        },
        "output": {
            "output_dir": str(out_dir / "sft"),
            "logging_steps": max(1, _int(hyper, "logging_steps", 5)),
            "save_steps": save_steps if save_steps > 0 else 500,
            "save_total_limit": 3,
            "plot_loss": True,
            "overwrite_output_dir": True,
            "report_to": "none",
        },
        "train": {
            "per_device_train_batch_size": _int(hyper, "batch_size", 2),
            "gradient_accumulation_steps": _int(hyper, "gradient_accumulation", 8),
            "learning_rate": _float(hyper, "learning_rate", 1.0e-4),
            "num_train_epochs": float(epochs),
            "lr_scheduler_type": hyper.get("lr_scheduler_type", "cosine"),
            "warmup_ratio": _float(hyper, "warmup_ratio", 0.1),
            "weight_decay": _float(hyper, "weight_decay", 0.01),
            "max_grad_norm": _float(hyper, "max_grad_norm", 1.0),
            "optim": hyper.get("optim", "adamw_torch"),
            "gradient_checkpointing": _bool(hyper, "gradient_checkpointing", True),
            "seed": _int(hyper, "seed", 42),
            "bf16": True,
            "ddp_timeout": 180000000,
        },
    }
    if template:
        yml["dataset"]["template"] = template

    # NEFTune：给 embedding 加噪声，指令跟随与生成多样性更好；0 表示关闭（LLaMA-Factory 里 0 即禁用）
    neftune = _float(hyper, "neftune_noise_alpha", 0)
    if neftune > 0:
        yml["train"]["neftune_noise_alpha"] = neftune

    # 注意力实现：fa2 需要 flash-attn 已安装，装不上时 LLaMA-Factory 会报错，
    # 因此只显式指定 sdpa/disabled，auto 交给框架自己挑。
    flash_attn = (hyper.get("flash_attn") or "auto").lower()
    if flash_attn == "fa2":
        yml["train"]["flash_attn"] = "fa2"
    elif flash_attn == "sdpa":
        yml["train"]["flash_attn"] = "sdpa"
    elif flash_attn == "disabled":
        yml["train"]["flash_attn"] = "disabled"

    if do_eval:
        yml["eval"] = {
            "val_size": val_ratio,
            "per_device_eval_batch_size": 1,
            "eval_strategy": "steps",
            "eval_steps": eval_steps,
            "save_strategy": "steps",
        }
        # 早停要求「保存步数是评估步数的整数倍」且 load_best_model_at_end=True，
        # 服务端已保证倍数关系，这里再兜一次防止手改 hyper_params 后炸掉。
        if patience > 0:
            yml["train"]["load_best_model_at_end"] = True
            yml["train"]["metric_for_best_model"] = "eval_loss"
            yml["train"]["greater_is_better"] = False
            yml["train"]["early_stopping_patience"] = patience
    else:
        yml["eval"] = {"val_size": 0.0, "eval_strategy": "no"}

    if method == "qlora":
        yml["method"]["quantization_bit"] = _int(hyper, "quant_bit", 4)
        yml["method"]["quantization_method"] = hyper.get("quant_method", "bitsandbytes")

    path = out_dir / "train.yaml"
    out_dir.mkdir(parents=True, exist_ok=True)
    path.write_text(yaml.dump(yml, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return path


def _register_dataset(llamafactory_data_dir: Path, ds_key: str, ds_file: Path, fmt: str):
    llamafactory_data_dir.mkdir(parents=True, exist_ok=True)
    info_path = llamafactory_data_dir / "dataset_info.json"
    info = {}
    if info_path.exists():
        info = json.loads(info_path.read_text(encoding="utf-8"))
    if "sharegpt" in (fmt or ""):
        info[ds_key] = {
            "file_name": str(ds_file),
            "formatting": "sharegpt",
            "columns": {"messages": "conversations"},
            "tags": {"role_tag": "from", "content_tag": "value", "user_tag": "human", "assistant_tag": "gpt"},
        }
    else:
        info[ds_key] = {
            "file_name": str(ds_file),
            "formatting": "alpaca",
            "columns": {"prompt": "instruction", "query": "input", "response": "output"},
        }
    info_path.write_text(json.dumps(info, ensure_ascii=False, indent=1), encoding="utf-8")


_TQDM_RE = re.compile(r"(\d+)%\|")
_LOSS_RE = re.compile(r"'loss': ([\d.eE+-]+).*?'epoch': ([\d.eE+-]+)")
_EVAL_RE = re.compile(r"'eval_loss': ([\d.eE+-]+)")
_PARAMS_RE = re.compile(r"trainable params:\s*([\d,]+).*?all params:\s*([\d,]+)")


def _run_and_stream(client, job_uuid, epoch, cmd: list, total_hint: float, info: dict, workdir: Path = None):
    """子进程跑 llamafactory 命令，解析 stdout 的 tqdm 百分比、loss 与可训练参数量"""
    proc = subprocess.Popen(cmd, cwd=workdir, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace", bufsize=1)
    last_push = 0.0
    last_loss, last_eval, cur_epoch = None, None, 0
    assert proc.stdout is not None
    for line in proc.stdout:
        print(line.rstrip())
        m = _LOSS_RE.search(line)
        if m:
            last_loss = float(m.group(1))
            cur_epoch = min(int(math.ceil(float(m.group(2)))), max(1, int(total_hint)))
        m = _EVAL_RE.search(line)
        if m:
            last_eval = float(m.group(1))
        m = _PARAMS_RE.search(line)
        if m:
            info["trainable_params"] = int(m.group(1).replace(",", ""))
            info["total_params"] = int(m.group(2).replace(",", ""))
        m = _TQDM_RE.search(line)
        pct = float(m.group(1)) if m else None
        now = time.time()
        if (pct is not None or last_loss is not None) and now - last_push >= 10:
            last_push = now
            overall = pct if pct is not None else 0
            if _progress(client, job_uuid, epoch, overall, cur_epoch, last_loss, last_eval):
                proc.terminate()
                raise RuntimeError("stop requested by admin")
    code = proc.wait()
    if code != 0:
        raise RuntimeError(f"{cmd[0]} {cmd[1] if len(cmd) > 1 else ''} exited with code {code}")


def _collect_metrics(client, job_uuid, epoch, out_dir: Path, info: dict, hyper: dict, extra_log=None):
    """从 trainer_state.json 汇总训练报告：最终/最优 loss、总步数、耗时、最佳检查点。
    这份报告会落到 adi_finetune_job.metrics，用来回答「这次训练到底有没有变好」。"""
    metrics = {
        "base_model": info.get("base_model"),
        "method": info.get("method"),
        "gpu": info.get("gpu"),
        "preset": hyper.get("preset", ""),
        "lora_rank": hyper.get("lora_rank"),
        "lora_alpha": hyper.get("lora_alpha"),
        "learning_rate": hyper.get("learning_rate"),
        "batch_size": hyper.get("batch_size"),
        "gradient_accumulation": hyper.get("gradient_accumulation"),
        "cutoff_len": hyper.get("cutoff_len"),
        "num_epochs": hyper.get("num_epochs"),
    }
    state_path = out_dir / "sft" / "trainer_state.json"
    if state_path.exists():
        try:
            state = json.loads(state_path.read_text(encoding="utf-8"))
            history = state.get("log_history") or []
            train_losses = [h["loss"] for h in history if isinstance(h.get("loss"), (int, float))]
            epochs_done = [h["epoch"] for h in history if isinstance(h.get("epoch"), (int, float))]
            last = history[-1] if history else {}
            if train_losses:
                metrics["train_loss"] = round(train_losses[-1], 4)
                metrics["first_train_loss"] = round(train_losses[0], 4)
            eval_entries = [h for h in history if isinstance(h.get("eval_loss"), (int, float))]
            if eval_entries:
                metrics["eval_loss"] = round(eval_entries[-1]["eval_loss"], 4)
                best_eval = min(eval_entries, key=lambda h: h["eval_loss"])
                metrics["best_eval_loss"] = round(best_eval["eval_loss"], 4)
                metrics["best_step"] = best_eval.get("step")
                # eval_loss 已经回升 = 过拟合开始，是判断「该不该用这个产物」的直接依据
                metrics["eval_loss_increased"] = eval_entries[-1]["eval_loss"] > best_eval["eval_loss"] + 1e-4
            metrics["total_steps"] = state.get("global_step")
            metrics["epochs_done"] = round(max(epochs_done), 2) if epochs_done else None
            metrics["best_checkpoint"] = Path(state["best_model_checkpoint"]).name \
                if state.get("best_model_checkpoint") else None
            for k in ("train_runtime", "train_samples_per_second", "total_flos"):
                if isinstance(last.get(k), (int, float)):
                    metrics[k] = round(last[k], 3)
            if isinstance(last.get("learning_rate"), (int, float)):
                metrics["final_learning_rate"] = last["learning_rate"]
        except Exception as e:  # 报告只是佐证，解析失败不能影响任务终态
            print(f"[trainer][warn] parse trainer_state failed: {e}")
    metrics.update({k: v for k, v in info.items() if v is not None})
    if extra_log:
        metrics.update(extra_log)
    return metrics


def _zip_dir(src_dir: Path, target: Path) -> Path:
    """把 LoRA adapter 目录打成 zip（体积小，适合回传到服务端长期保存）"""
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in sorted(src_dir.rglob("*")):
            if f.is_file():
                zf.write(f, f.relative_to(src_dir))
    return target


_QUANT_NAME = {"q4_k_m": "Q4_K_M", "q5_k_m": "Q5_K_M", "q8_0": "Q8_0", "f16": "F16"}


def _export_gguf(merged_dir: Path, out_dir: Path, quant: str, log) -> Path:
    """merged HF 目录 -> GGUF。先转 f16，再用 llama-quantize 压到目标档位；
    找不到 llama.cpp 工具链就退回 f16 并如实告知（不假装成功）。"""
    convert_script = Path("/opt/llama.cpp/convert_hf_to_gguf.py")
    if not convert_script.exists():
        raise RuntimeError("llama.cpp convert_hf_to_gguf.py not found at /opt/llama.cpp")
    f16_path = out_dir / "model-f16.gguf"
    subprocess.run(["python", str(convert_script), str(merged_dir), "--outfile", str(f16_path),
                    "--outtype", "f16"], check=True)
    quant_name = _QUANT_NAME.get((quant or "q4_k_m").lower(), "Q4_K_M")
    if quant_name == "F16":
        return f16_path
    for candidate in ("/opt/llama.cpp/build/bin/llama-quantize", "/opt/llama.cpp/llama-quantize"):
        if Path(candidate).exists():
            final_path = out_dir / f"model-{quant_name.lower()}.gguf"
            subprocess.run([candidate, str(f16_path), str(final_path), quant_name], check=True)
            return final_path
    log(f"llama-quantize not found, keep f16 gguf: {f16_path}")
    return f16_path


def train_job(client, job: dict, data_dir: Path):
    import llamafactory  # noqa: F401  环境校验：真实模式必须先装 LLaMA-Factory

    job_uuid = job["jobUuid"]
    epoch = job["workerEpoch"]
    hyper = job.get("hyperParams") or {}
    work = data_dir / job_uuid
    work.mkdir(parents=True, exist_ok=True)
    log = lambda msg: _log(client, job_uuid, epoch, msg)  # noqa: E731

    info = {
        "base_model": job.get("baseModelName"),
        "method": job.get("method"),
        "preset": hyper.get("preset", ""),
    }
    try:
        import torch
        info["gpu"] = torch.cuda.get_device_name(0) if torch.cuda.is_available() else "cuda-unavailable"
    except Exception:
        info["gpu"] = "unknown"

    # 1. 下载数据集
    ds_file = work / "dataset.jsonl"
    if job.get("datasetFileUuid"):
        client.download_dataset(job["datasetFileUuid"], ds_file)
    _log(client, job_uuid, epoch, f"dataset ready: {ds_file}")

    # 2. 注册数据集到 LLaMA-Factory（llamafactory-cli data 目录）
    import llamafactory.extras.constants as lf_const  # 延迟导入拿默认 data 目录
    lf_data_dir = Path(lf_const.DATA_CONFIG)
    ds_key = f"adi_{job_uuid[:16]}"
    _register_dataset(lf_data_dir, ds_key, ds_file, job.get("datasetFormat", "alpaca-jsonl"))

    # 3. 渲染训练 YAML
    out_dir = work / "output"
    template = _resolve_template(hyper, job.get("baseModelName"))
    train_yaml = _render_train_yaml(job, out_dir, ds_key, template)
    num_epochs = float(_int(hyper, "num_epochs", 3))
    _log(client, job_uuid, epoch,
         f"train yaml rendered: {train_yaml}\n[trainer] hyper: {json.dumps(hyper, ensure_ascii=False)}")

    # 4. 训练
    _run_and_stream(client, job_uuid, epoch, ["llamafactory-cli", "train", str(train_yaml)],
                    num_epochs, info, work)

    adapter_dir = out_dir / "sft"
    do_gguf = _bool(hyper, "export_gguf", True)
    do_merge = _bool(hyper, "export_merged", True) or do_gguf
    merged_dir = out_dir / "merged"
    gguf_path = None

    # 5. 合并导出（LoRA -> 完整模型目录；GGUF 转换也以它为输入）
    if do_merge:
        export_yaml = out_dir / "export.yaml"
        export_yaml.write_text(yaml.dump({
            "model": {"model_name_or_path": job.get("baseModelName"), "adapter_name_or_path": str(adapter_dir),
                      "finetuning_type": "lora", "trust_remote_code": True,
                      **({"template": template} if template else {})},
            "export": {"export_dir": str(merged_dir), "export_size": 4, "export_device": "cpu",
                       "export_legacy_format": False},
        }, allow_unicode=True, sort_keys=False), encoding="utf-8")
        _run_and_stream(client, job_uuid, epoch, ["llamafactory-cli", "export", str(export_yaml)],
                        num_epochs, info, work)
        _progress(client, job_uuid, epoch, 95, int(num_epochs), None, None)

    # 6. GGUF 量化（llama.cpp 可选）
    if do_gguf:
        try:
            gguf_path = _export_gguf(merged_dir, out_dir, hyper.get("gguf_quant", "q4_k_m"), log)
            _log(client, job_uuid, epoch, f"gguf generated: {gguf_path}")
        except Exception as e:
            # GGUF 只是部署便利，失败不该让整个任务判死：合并模型已产出，如实记录原因
            _log(client, job_uuid, epoch, f"[warn] gguf export skipped: {e}")
            info["gguf_error"] = str(e)[:200]
            gguf_path = None

    # 7. 训练报告 + 产物回传（adapter zip 必传；GGUF / merged 走 GPU 侧路径登记）
    metrics = _collect_metrics(client, job_uuid, epoch, out_dir, info, hyper)
    adapter_zip = _zip_dir(adapter_dir, data_dir / job_uuid / "adapter.zip") if adapter_dir.exists() else None
    name = job.get("jobName") or job_uuid
    if gguf_path is not None:
        client.complete(job_uuid, epoch, adapter_zip=adapter_zip, artifact_type="gguf",
                        name=name, external_path=str(gguf_path),
                        external_size=gguf_path.stat().st_size,
                        remark=f"gguf={hyper.get('gguf_quant')}", metrics=metrics)
    elif do_merge and merged_dir.exists():
        client.complete(job_uuid, epoch, adapter_zip=adapter_zip, artifact_type="merged",
                        name=name, external_path=str(merged_dir),
                        remark="merged fp16 (gguf skipped)", metrics=metrics)
    else:
        client.complete(job_uuid, epoch, adapter_zip=adapter_zip, artifact_type="adapter",
                        name=name, remark="adapter only", metrics=metrics)
    _log(client, job_uuid, epoch, f"job completed, metrics: {json.dumps(metrics, ensure_ascii=False)}")
