# syntax=docker/dockerfile:1
# 构建前台与后台两个 SPA，并由 Nginx 提供静态文件（配置文件由 compose 挂载 deploy/*.conf）。
FROM node:20-alpine AS blog
WORKDIR /app
COPY kuailemao-blog/package.json kuailemao-blog/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY kuailemao-blog/ ./
RUN npm run build

FROM node:20-alpine AS admin
WORKDIR /app
COPY kuailemao-admin/package.json kuailemao-admin/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY kuailemao-admin/ ./
RUN npm run build

FROM nginx:1.28-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=blog /app/dist /usr/share/nginx/html/blog
COPY --from=admin /app/dist /usr/share/nginx/html/admin
