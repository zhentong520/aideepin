# ADI Finetune Worker

aideepin 微调训练 Worker：从后端拉取训练任务，在 GPU 机器上以 LLaMA-Factory 执行 LoRA/QLoRA 训练，并把进度、日志、产物和**训练报告**回传。

## 两种模式

| 模式 | 用途 | 依赖 |
|------|------|------|
| `--mode mock`（默认） | 无 GPU 环境跑通全链路：领取任务→下载数据集→模拟进度/loss/日志→回传伪 adapter zip + 登记 GGUF 路径 + 训练报告 | 仅 `requests` |
| `--mode real` | 真实训练：渲染 LLaMA-Factory YAML → `llamafactory-cli train` → 按开关合并/量化导出 → 回传 adapter zip + 训练报告 | GPU + LLaMA-Factory + （可选）llama.cpp |

## 配置

复制 `config.example.json` 为 `config.json`，或用环境变量（优先）：

```bash
export ADI_SERVER_URL=http://<aideepin后端>:9999
export ADI_API_KEY=ext-xxxxxxxx        # 管理端「模型微调 → 模型产物 → Worker Key」生成
export ADI_WORKER_ID=gpu-node-01       # 可选，缺省 主机名-pid
export ADI_POLL_INTERVAL=10            # 可选，无任务时轮询间隔（秒）
```

API Key 获取：aideepin 管理端登录 → `POST /api/admin/finetune/workerKey/generate`
（响应里的 rawKey 即 `ext-` 开头的明文，只显示一次；也可在页面上「查看明文」）。

## 运行

```bash
pip install -r requirements.txt

# mock 模式（本机无 GPU 即可验证全链路）
python worker.py --mode mock

# 只领一个任务就退出（联调用）
python worker.py --mode mock --once

# 真实训练（GPU 机器）
python worker.py --mode real
```

环境变量加速（国内拉模型）：

```bash
export HF_ENDPOINT=https://hf-mirror.com
export USE_MODELSCOPE=1   # 或改用 ModelScope 下载
```

## GPU 机器部署（AutoDL 实例为例）

```bash
git clone <本仓库> && cd training-worker
conda create -n adi-ft python=3.10 -y && conda activate adi-ft
pip install -r requirements.txt
# LLaMA-Factory（CUDA 12.x 环境）
pip install "llamafactory[torch,metrics]"
# llama.cpp（GGUF 量化，可选）
apt install -y build-essential && git clone --depth 1 https://github.com/ggml-org/llama.cpp /opt/llama.cpp && cd /opt/llama.cpp && cmake -B build && cmake --build build -j

python worker.py --mode real
```

## 超参对照表（由服务端 `hyper_params` 下发，Worker 只负责翻译成 YAML）

服务端在创建任务时已填好默认值并**派生好步数**，Worker 不再自行决定默认值——避免两边默认值打架。

| hyper_params 键 | 对应 YAML 字段 | 默认 | 说明 |
|---|---|---|---|
| `lora_rank` | `lora_rank` | 16 | LoRA 秩。数据量少用 8，常规 16，强风格迁移 32 |
| `lora_alpha` | `lora_alpha` | 32 | 缩放系数，经验取 2×rank |
| `lora_dropout` | `lora_dropout` | 0.05 | 小数据集可提到 0.1 |
| `lora_target` | `lora_target` | all | `all` 或逗号分隔白名单（q_proj,k_proj,...） |
| `use_rslora` | `use_rslora` | false | rank ≥ 64 时建议开启 |
| `use_dora` | `use_dora` | false | 效果更好但慢约 30% |
| `learning_rate` | `learning_rate` | 1e-4 | LoRA SFT 建议 1e-4~2e-4 |
| `num_epochs` | `num_train_epochs` | 3 | 3~5 轮；配合早停可放宽 |
| `batch_size` | `per_device_train_batch_size` | 2 | 显存第一决定因素 |
| `gradient_accumulation` | `gradient_accumulation_steps` | 8 | 有效批 = batch × 本值，建议 16~32 |
| `lr_scheduler_type` | `lr_scheduler_type` | cosine | cosine / linear / constant_with_warmup 等 |
| `warmup_ratio` | `warmup_ratio` | 0.1 | 0.03~0.1 |
| `weight_decay` | `weight_decay` | 0.01 | 小数据集可到 0.05~0.1 |
| `max_grad_norm` | `max_grad_norm` | 1.0 | loss 尖刺时先调小 |
| `optim` | `optim` | adamw_torch | 低显存用 `paged_adamw_8bit` |
| `cutoff_len` | `cutoff_len` | 2048 | 覆盖 95% 样本长度 |
| `val_ratio` | `val_size` | 0.05 | =0 则关闭 eval，同时禁用早停 |
| `packing` | `packing` | false | 短样本场景开，吞吐常翻倍 |
| `neftune_noise_alpha` | `neftune_noise_alpha` | 0（关闭） | 5~10 提升指令跟随与多样性 |
| `gradient_checkpointing` | `gradient_checkpointing` | true | 激活显存降到约 1/3，慢约 20% |
| `flash_attn` | `flash_attn` | auto | auto / fa2 / sdpa / disabled |
| `seed` | `seed` | 42 | 复现性 |
| `logging_steps` | `logging_steps` | 自动 | 服务端按总步数派生 |
| `save_steps` | `save_steps` | 自动 | 有 eval 时自动对齐为 eval_steps 的整数倍（HF 硬要求） |
| `eval_steps` | `eval_steps` | 自动 | 约总步数 1/10，最小 5 |
| `early_stopping_patience` | `early_stopping_patience` | 0（关闭） | 需 `val_ratio>0`；开启后自动带 `load_best_model_at_end` |
| `quant_bit` / `quant_method` | `quantization_bit` / `quantization_method` | 4 / bitsandbytes | 仅 QLoRA |
| `export_merged` | — | true | 合并出 fp16 完整模型目录 |
| `export_gguf` | — | true | 转 GGUF；`export_merged=false` 时也会先合并（GGUF 需要 merged 作输入） |
| `gguf_quant` | — | q4_k_m | q4_k_m / q5_k_m / q8_0 / f16 |
| `template` | `template` | 空=自动 | 空则按模型名推断（qwen / llama3 …） |

**模板兜底**：`template` 不在当前 LLaMA-Factory 版本的 `TEMPLATES` 表里时，Worker 会打警告并回退为框架自动识别，而不是直接报错——升级 LLaMA-Factory 后老任务不会集体失效。

## 训练报告（metrics）

训练结束（`/job/{uuid}/complete`）时随产物一起上报，服务端落在 `adi_finetune_job.metrics`，管理端任务详情「训练报告」页签展示：

| 字段 | 来源 |
|---|---|
| `train_loss` / `first_train_loss` | `trainer_state.json` 的 `log_history` |
| `eval_loss` / `best_eval_loss` / `best_step` / `eval_loss_increased` | 同上，`eval_loss_increased=true` 即已过拟合 |
| `total_steps` / `epochs_done` / `final_learning_rate` | `global_step` / `log_history` |
| `best_checkpoint` | `best_model_checkpoint` |
| `train_runtime` / `train_samples_per_second` | 训练末行日志 |
| `trainable_params` / `total_params` | 训练启动日志的 `trainable params:` 行 |
| `gpu` / `base_model` / `method` / `preset` | Worker 环境与任务信息 |
| `gguf_error` | GGUF 转换失败时的原因（不影响任务成功终态） |

## 协议速查（服务端 /ext/v1/finetune/*，Authorization 头带 ext- key）

| 端点 | 说明 |
|------|------|
| `POST /worker/heartbeat` | Worker 心跳 {workerId, gpuInfo} |
| `POST /job/claim` | 领取一个 pending 任务，返回 null 表示无任务 |
| `GET /dataset/{fileUuid}/download` | 下载数据集 JSONL |
| `POST /job/{uuid}/progress?workerEpoch=` | 进度回写（心跳合一），响应带 `stopRequested` |
| `POST /job/{uuid}/log?workerEpoch=` | 追加训练日志 |
| `POST /job/{uuid}/complete?workerEpoch=` | 成功终态 + 产物 + `metrics`(JSON 字符串)；file=adapter zip，externalPath=GPU 侧 GGUF/merged 路径 |
| `POST /job/{uuid}/finish?workerEpoch=&status=failed\|stopped` | 失败/中止终态 |

关键语义：
- `workerEpoch`：claim 响应里返回，之后每次回传；任务被服务端回收重派后，旧 Worker 的迟到写入会被拒绝（`accepted=false`，应立即退出）。
- 进度响应 `stopRequested=true`：管理端请求停止，Worker 应尽快中止并上报 `stopped`。
- 心跳超时（默认 5 分钟无进度回写）任务会被重置回 pending 重派；训练超过 24 小时强制失败（熔断）。

## 产物说明

| type | 位置 | 说明 |
|---|---|---|
| `adapter` | 服务端 `finetune/artifacts/*.zip` | LoRA 权重，几十 MB，由 Worker 上传 |
| `merged` | GPU 机器上 | 合并后的 fp16 完整模型，体积≈基座 |
| `gguf` | GPU 机器上 | llama.cpp 量化模型，可在「模型产物」页注册为模型（配合 `ollama create`） |
