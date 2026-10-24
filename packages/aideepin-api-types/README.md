# @aideepin/aideepin-api-types

OPT-12（前后端契约化）：后端 DTO 的唯一类型事实源。

- `openapi.json` — 从运行中的后端导出的 OpenAPI 3.1 契约（dev profile，springdoc）
- `src/schema.d.ts` — `openapi-typescript` 生成的类型（**不要手改**）

## 更新流程（后端 DTO 变更后）

```bash
# 1. 导出契约（后端 dev 起在 :9999；容器栈走 nginx 80 的 /api 前缀时改 URL）
node scripts/export-openapi.mjs

# 2. 重新生成类型
pnpm --filter aideepin-api-types generate
```

## 消费方式（两端 workspace 依赖）

```ts
import type { paths, components } from 'aideepin-api-types'
type KbDetail = components['schemas']['KbInfoResp']
```

手写 typings 与契约重叠的条目逐步切换，不强制一次性替换（见架构优化方案 OPT-12）。

## CI 契约门禁

ci.yml 的 docker-build job 里 `assert-contract-types` 步骤：用仓内 `openapi.json`
重新生成 schema.d.ts 并 `git diff --exit-code`——后端 DTO 变了但没重新生成类型时 PR 红。
