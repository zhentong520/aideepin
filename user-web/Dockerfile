FROM node:22-alpine AS build

RUN npm install pnpm -g

WORKDIR /app

COPY ./package.json ./pnpm-lock.yaml ./pnpm-workspace.yaml ./

RUN pnpm install --no-frozen-lockfile

COPY . .

# 构建含 type-check 与 i18n 检查（质量门禁，不允许跳过）
RUN pnpm run build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
