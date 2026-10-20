# T6-15 委派评测集

评测多 Agent 委派质量：给定任务描述，被测路由/委派逻辑应选择正确的 agent。产出**委派正确率**。

## 组成

| 文件 | 说明 |
| --- | --- |
| `dataset.json` | 固定题集（20 题）：`task` 任务描述 → `expected_agent` 应委派给的 agent（`aliases` 可接受别名）+ `candidates` 候选列表 |
| `run_delegation_eval.py` | 跑分脚本：逐题调被测端点 → 计分 → 输出 JSON 报告 |
| `reports/` | 报告输出目录（每次跑分生成 `report-<时间戳>.json`） |

计分核心在 Java 侧同语义实现：`server/adi-common/src/main/java/com/moyz/adi/common/eval/DelegationScorer.java`（配套单测 `DelegationScorerTest`）。

## 题集语义

- `expected_agent` / `aliases` / `candidates` 中的 agent 名是**示例占位**（translator / coder / kb-qa 等）。接入真实环境时替换为系统中实际存在的 agent（Character）标识，题目语义不变。
- `expected_agent` 必须出现在 `candidates` 中（校验器强制），否则被测端无正确答案可选。

## 跑分方式

被测端点契约（委派/路由逻辑的 HTTP 包装）：

```
POST {task, candidates: [...]}
→ 200 {"agent": "<predicted>"}
```

```bash
# 1) 只校验题集结构（不评分、不产出分数）
python run_delegation_eval.py --dataset dataset.json --validate

# 2) 真实跑分
export DELEGATION_EVAL_ENDPOINT=http://localhost:9999/...   # 委派选择端点
export DELEGATION_EVAL_TOKEN=<api-key>                      # 可选
python run_delegation_eval.py --dataset dataset.json
```

## 报告结构

```json
{
  "generatedAt": "2026-10-20T12:00:00",
  "totalCases": 20,
  "evaluated": 20,
  "callFailures": [],
  "correct": 17,
  "accuracy": 0.85,
  "results": [
    {"caseId": "DEL-001", "expected": "translator", "predicted": "translator", "correct": true}
  ]
}
```

## 铁律 9：不伪造分数

- 无端点时脚本以退出码 3 终止，**不产出任何报告**；
- 全部题目调用失败时以退出码 4 终止，同样不产出报告；
- 调用失败的题目不计入分母，并在报告 `callFailures` 中如实列出；
- 空题集时 Java 计分器 `accuracy = -1`（单测覆盖），不允许输出 100% 假绿。

## 当前状态（2026-10-20）

- 题集与计分器已交付并有单测；
- 项目当前尚无独立委派路由端点（`delegate_agent` 属 T7-02 范围），故**未产出任何正确率数字**；
- T7-02 落地后，为其选择逻辑包一层上述契约的端点即可直接跑分。
