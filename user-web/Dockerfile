# 构建上下文 = 仓库根（需包含 packages/aideepin-tokens，见 docker/docker-compose.yml context 配置）
FROM node:22-alpine AS build

RUN npm install pnpm -g

WORKDIR /app

COPY user-web/package.json user-web/pnpm-lock.yaml user-web/pnpm-workspace.yaml user-web/
# aideepin-tokens 以 link:../packages/aideepin-tokens 被 user-web 引用，必须随上下文进入镜像
COPY packages/ packages/

WORKDIR /app/user-web

RUN pnpm install --frozen-lockfile

COPY user-web/ ./

# 构建含 type-check 与 i18n 检查（质量门禁，不允许跳过）
RUN pnpm run build

FROM nginx:alpine

COPY --from=build /app/user-web/dist /usr/share/nginx/html
