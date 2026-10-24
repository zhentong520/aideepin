# 构建上下文 = 仓库根（需包含 packages/aideepin-tokens，见 docker/docker-compose.yml context 配置）
FROM node:22-alpine AS build

RUN npm install pnpm -g

WORKDIR /app

COPY admin-web/package.json admin-web/pnpm-lock.yaml admin-web/pnpm-workspace.yaml admin-web/
# aideepin-tokens 以 link:../packages/aideepin-tokens 被 admin-web 引用，必须随上下文进入镜像
COPY packages/ packages/

WORKDIR /app/admin-web

RUN pnpm install --frozen-lockfile

COPY admin-web/ ./

RUN pnpm run build

FROM nginx:alpine

COPY --from=build /app/admin-web/dist /usr/share/nginx/html
