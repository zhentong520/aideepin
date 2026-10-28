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

# OPT-05 后 .env 不入库：CI/干净上下文构建必须生成 env 文件（与本地 .env.production 对齐，nginx 挂 /admin/）
RUN cp .env.example .env && \
    sed -e 's|^VITE_GLOB_SITE_URL = .*|VITE_GLOB_SITE_URL = /|' \
        -e 's|^VITE_PUBLIC_PATH = .*|VITE_PUBLIC_PATH = /admin/|' \
        -e 's|^VITE_BASE_URL = .*|VITE_BASE_URL = /admin|' \
        .env.example > .env.production

RUN pnpm run build

FROM nginx:alpine

COPY --from=build /app/admin-web/dist /usr/share/nginx/html
