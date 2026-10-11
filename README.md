# lcblog — 个人技术博客（前后端分离）

访客阅读已发布的文章、浏览相册；唯一的管理员在 `/admin/` 后台写作、管理分类标签和文件。

| 部分 | 技术 | 目录 |
| --- | --- | --- |
| 后端 REST API | Java 17、Spring Boot 4.1、Spring Security（Session + CSRF）、MyBatis、MySQL 8.4、Flyway、阿里云 OSS SDK | `blog-backend/` |
| 前台 SPA | Vue 3 + TypeScript + Vite + Vue Router + Element Plus（基于 Ruyu-Blog 修改） | `blog-frontend/kuailemao-blog` |
| 后台 SPA | Vue 3 + TypeScript + Vite + Vue Router + Ant Design Vue（基于 Ruyu-Blog / Antdv Pro 修改） | `blog-frontend/kuailemao-admin` |
| 部署 | Docker Compose：Nginx（静态文件 + `/api` 反向代理）→ Spring Boot → MySQL | `compose.yml`、`deploy/` |

前端复用来源、许可证和修改清单见 `NOTICE` 与 `blog-frontend/REUSE.md`。

```
blog-backend/src/main/java/com/lcblog
├── auth      管理员账号、一次性初始化、会话状态接口
├── article   文章 / 分类 / 标签、Markdown 净化渲染、公开与后台接口、sitemap
├── media     文件元数据、OSS 直传与核验、公开 / 私有访问、清理任务
├── album     相册与照片
├── common    统一响应 {code,msg,data}、错误处理、分页、slug
└── config    配置绑定与安全配置
```

## 1. 本地开发

需要 JDK 17、Node.js 20+、MySQL 8。三个进程可以分别启动：

```powershell
# 1) 后端（http://localhost:8080，只提供 /api）
$env:DB_PASSWORD = '<数据库密码>'
$env:BLOG_ADMIN_USERNAME = 'admin'
$env:BLOG_ADMIN_PASSWORD = '<至少 12 位>'
mvn -f blog-backend/pom.xml spring-boot:run

# 2) 前台（http://127.0.0.1:5173，Vite 把 /api 代理到 8080）
cd blog-frontend/kuailemao-blog; npm ci; npm run dev

# 3) 后台（http://127.0.0.1:5174/admin/）
cd blog-frontend/kuailemao-admin; npm ci; npm run dev
```

数据库只需提前建库和账号，表由 Flyway 在后端启动时创建：

```sql
CREATE DATABASE lcblog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'lcblog'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON lcblog.* TO 'lcblog'@'localhost';
```

前后端独立构建：在项目根目录执行后端 `mvn -f blog-backend/pom.xml package`（产物在 `blog-backend/target/`），前台和后台分别 `npm run build`（产物在各自的 `dist/`）。

### 一体化运行（Docker Compose）

```bash
cp .env.example .env      # 填写 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD、BLOG_ADMIN_PASSWORD
docker compose up -d --build
```

前台 http://localhost ，后台 http://localhost/admin/ 。`nginx` 镜像会在构建时编译两个前端。

## 2. 管理员与认证

- 只有一名管理员，不提供注册、读者账号、第三方登录和角色管理。
- 首次启动时通过 `BLOG_ADMIN_USERNAME` / `BLOG_ADMIN_PASSWORD` 创建（仅当 `admin_user` 为空），密码以 BCrypt 保存。
  创建后从环境中删除 `BLOG_ADMIN_PASSWORD`。
- 登录：`POST /api/auth/login`（表单 `username`、`password`），成功 200、失败 401，均为 JSON，不重定向。
- 会话：`JSESSIONID` Cookie（HttpOnly；`prod` profile 下 Secure；SameSite=Lax）。登录时会话 ID 轮换。
- CSRF：后端把令牌写入可读 Cookie `XSRF-TOKEN`，前端以 `X-XSRF-TOKEN` 请求头回传（axios 默认行为）。
  后台启动及登录前后会调用 `GET /api/auth/csrf` 确保 Cookie 存在；令牌失效时自动刷新一次并重试。
- 未登录访问 `/api/admin/**` 返回 401 JSON，CSRF 失败或无权限返回 403 JSON。后台路由守卫只改善体验，所有权限都由后端检查；
  未在 `SecurityConfig` 中声明的路径一律拒绝。

## 3. 接口一览

| 访客（无需登录） | 管理员（`/api/admin`） |
| --- | --- |
| `GET /api/public/site` 站点与站长信息 | `GET/POST /articles`、`GET/PUT/DELETE /articles/{id}`（`?publish=true` 保存并发布） |
| `GET /api/public/articles?page&category&tag&q` | `POST /articles/{id}/publish`、`/unpublish`、`GET /articles/{id}/export` |
| `GET /api/public/articles/pinned` | `POST /markdown/preview`（与前台相同的净化渲染） |
| `GET /api/public/articles/{slug}`（净化 HTML + 目录 + 附件） | `GET/POST /categories`、`PUT/DELETE /categories/{id}`；标签同理 |
| `GET /api/public/categories`、`/tags`、`/archive` | `GET /media`、`POST /media/uploads`、`POST /media/uploads/{id}/complete` |
| `GET /api/public/gallery?parentId&page` | `PUT /media/{id}/article`、`/visibility`，`DELETE /media/{id}`，`GET /media/{id}/download` |
| `GET /api/media/{id}`（公开文件跳转到短时签名地址） | `GET/POST /albums`、`PUT/DELETE /albums/{id}`、`/albums/{id}/photos` |
| `GET /sitemap.xml`、`/robots.txt` | `GET /api/auth/me`、`GET /api/auth/csrf`、`POST /api/auth/logout` |

所有响应为 `{code, msg, data}`，`code` 与 HTTP 状态码一致。

数据安全约定：

- 访客查询在 SQL 中固定 `status = 'PUBLISHED'` 或 `READY + PUBLIC + hidden = FALSE`（`PublicQueryGuardTest` 检查）。草稿不会出现在列表、搜索、
  站点地图、相邻文章中，按 slug 访问返回 404。
- 文章正文由后端渲染：CommonMark 转义原始 HTML 并清除危险链接，jsoup 白名单再次过滤，前端只展示该结果。
- 文件的稳定地址统一为 `/api/media/{id}`（复制的 Markdown、编辑器插图、封面、相册、删除引用检查都用它）。
  访客只能拿到就绪、公开、且所属文章已发布的文件；已登录的管理员通过同一地址拿到任意就绪文件，因此能在后台预览草稿图片。
- 关联到草稿文章的公开附件在发布前不可访问，取消发布后再次不可访问；私有文件只能由管理员下载。
- 新文章在保存前上传图片或封面时，编辑页会先自动保存草稿，让图片关联到这篇文章，随文章状态控制访问。
  文件管理页上传且不选文章的公开文件是独立公开素材，立即可见。
- 删除草稿：文件本体保留，外键把 `article_id` 置空，同一事务中把这些文件标记为 `hidden`，访客仍然拿不到。
  在文件管理页重新关联文章，或点“设为独立素材”后才解除。删除已发布文章不改变其附件的可见性。
- 删除文件前检查是否仍被使用：正文引用 `/api/media/{id}`、作为封面、作为附件关联到文章、被相册使用，任一成立即拒绝；
  删除相册不会删除图片文件。

个人信息在 `blog-backend/src/main/resources/application.yml` 的 `blog.profile`（名字、简介、研究方向、技术栈、项目、GitHub、头像、公告、建站日期），
目前是“【待填写】”占位，请改成你自己的信息。

## 4. OSS 配置

### 4.1 Bucket

创建两个 **私有** Bucket（读写权限设为“私有”，并开启“阻止公共访问”）：

| 用途 | 环境变量 | 对象前缀 |
| --- | --- | --- |
| 公开素材：文章图片、公开文档、公开压缩包 | `OSS_PUBLIC_BUCKET` | `upload/`（浏览器暂存）、`public/`（核验后） |
| 私有附件：草稿附件、不公开资料 | `OSS_PRIVATE_BUCKET` | `upload/`（浏览器暂存）、`private/`（核验后） |

还需要设置 `OSS_ENDPOINT`（如 `oss-cn-hangzhou.aliyuncs.com`）和 `OSS_REGION`。数据库只保存 bucket 与 object_key，
访问时再临时生成签名地址（默认 5 分钟有效）。

### 4.2 CORS（两个 Bucket 都要配置）

浏览器会直接 POST 到 OSS，所以只允许博客自己的来源：

| 项 | 值 |
| --- | --- |
| 来源 | `https://blog.example.com`（本地开发可临时加 `http://127.0.0.1:5174`） |
| 允许 Methods | `POST` |
| 允许 Headers | `*` |
| 暴露 Headers | `ETag` |
| 缓存时间 | `600` |

不要使用 `*` 作为来源。

### 4.3 凭据与最小权限

凭据只在服务端使用，读取顺序：

1. 部署在 ECS：给实例绑定 RAM 角色，设置 `OSS_ECS_RAM_ROLE=<角色名>`，不需要 AccessKey。
2. 其他环境：通过环境变量 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`（可选 `OSS_SESSION_TOKEN`）注入。

建议的 RAM 权限策略（把 bucket 名替换成你的）：

```json
{
  "Version": "1",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["oss:PutObject", "oss:GetObject", "oss:DeleteObject"],
    "Resource": [
      "acs:oss:*:*:your-public-bucket/upload/*",
      "acs:oss:*:*:your-public-bucket/public/*",
      "acs:oss:*:*:your-private-bucket/upload/*",
      "acs:oss:*:*:your-private-bucket/private/*"
    ]
  }]
}
```

复制对象（完成确认、公开/私有切换）需要对源对象的 `GetObject` 和对目标对象的 `PutObject`，上面的策略已覆盖。

建议在两个 Bucket 上各加一条生命周期规则作为兜底：前缀 `upload/` 的对象 1 天后删除；
如果开启了版本控制，再加一条“历史版本 1 天后删除”（同样限定 `upload/`），否则被覆盖或删除的暂存对象会以历史版本形式继续计费。

### 4.4 上传流程与限制

1. 管理员提交文件名、大小和可见性。后端根据扩展名白名单确定类型（不信任浏览器给出的 MIME），检查大小限制
   （图片 10 MB、文档 50 MB、压缩包 100 MB，可在 `blog.upload.*` 中调整），生成随机 UUID 作为暂存 key（`upload/…`），
   并写入一条 `PENDING` 记录。
2. 后端签发 10 分钟有效的表单策略，锁定暂存 key、精确大小、Content-Type 和 Content-Disposition。
3. 浏览器直传 OSS，然后调用 `/api/admin/media/uploads/{id}/complete`。
4. 后端只按记录 ID 和当前管理员查找 `PENDING` 记录，先确认暂存对象存在，再把它**复制到一个新的最终 key**
   （`public/…` 或 `private/…`，从未出现在任何上传授权中），然后核验这个副本的大小和文件头魔数，通过后记录改指向最终 key 并标记为 `READY`。
   大小或内容不符的文件会被丢弃。
5. 暂存 key 立即删除一次，同时登记为 `DELETE_PENDING`，等授权过期并超过 30 分钟宽限期后由清理任务再删一次。
6. 每 10 分钟运行一次清理任务：授权过期 30 分钟后仍未确认的上传会转为 `DELETE_PENDING`。删除时先删 OSS 对象再删记录，
   失败的会在下次重试。

为什么要复制：表单策略在有效期内可以被重复使用。如果直接把暂存 key 标记为 READY，核验通过后仍能用同一授权覆盖它，
换成未经核验的内容。`x-oss-forbid-overwrite` 在开启版本控制的 Bucket 上不生效，不能作为通用方案。
现在 READY 记录指向的对象只有后端能写，重复上传最多改写暂存 key，不会影响已核验的文件
（`MediaServiceTest.reusingUploadPolicyAfterCompleteCannotReplaceVerifiedFile`、`MediaLifecycleMySqlTest` 覆盖）。

公开/私有切换同样复制到目标 Bucket 中一个**全新的 key**，然后在一个事务内让记录指向新对象（乐观检查旧 key），
并把旧对象登记为待删除，提交后尝试立即删除旧对象，失败则交给清理任务。由于每次切换都用新 key：

- PUBLIC→PRIVATE→PUBLIC 可以连续进行，不会和仍在 `DELETE_PENDING` 中的旧路径撞唯一约束；
- 复制失败或数据库失败时记录仍指向原对象，原文件始终可用，只撤销本次复制出的新对象；
- 撤销前先确认记录没有指向新对象（提交结果未知时），确认不了就留下孤立对象而不删，避免误删当前有效文件；
- 两个并发切换只有一个能通过乐观检查，失败的一方只删除自己复制出的对象；
- 清理任务只删除 `DELETE_PENDING` 记录中的 key，而唯一约束保证这些 key 不会同时被 `READY` 记录使用。

白名单：jpg/jpeg/png/gif/webp；pdf/txt/md/doc/docx/xls/xlsx/ppt/pptx；zip/7z/gz/tgz/tar/rar。
SVG、HTML 等可执行内容不允许上传。只有图片内联展示，其余文件都以附件下载，并保留原始文件名。压缩包不会被解压。

### 4.5 可选：CDN

为公开 Bucket 开通 CDN，源站选择该 OSS Bucket 并开启“私有 Bucket 回源”，然后设置
`OSS_PUBLIC_BASE_URL=https://static.example.com`。之后 `/api/media/{id}` 会跳转到固定的 CDN 地址，不再生成签名 URL。
注意：

- 把文件从公开改为私有后，需要在 CDN 控制台刷新对应 URL 的缓存。
- CDN 只应回源 `public/` 前缀：在 CDN 上对 `/upload/*` 配置访问拒绝（或 URL 鉴权），避免未经核验的暂存对象被 CDN 直接提供。
- 草稿附件和“已隐藏”文件也在公开 Bucket 的 `public/` 下，靠随机 key 不可猜测；开启 CDN 后，任何拿到完整 key 的人都能绕过
  “文章未发布”检查。对这类内容要求更严时不要开启 CDN，继续使用后端签发的短时签名地址。

### 4.6 不依赖真实 OSS 的验证

应用与 OSS 之间只通过 `OssGateway` 一个接口交互。`MediaServiceTest` 用内存假实现验证完成确认、覆盖边界和切换的各个分支；
`MediaLifecycleMySqlTest` 在真实 MySQL 上配合同一个假 OSS 跑完整流程（见第 7 节）。
未配置 OSS 时站点照常运行，文件功能会提示“OSS 未配置”。

真实云服务的集成检查需要手动完成（本仓库没有真实凭据）：配置好 Bucket、CORS 和 RAM 权限后，在后台上传
一张图片、一个 PDF、一个私有 zip，然后确认：
① 草稿中的图片在后台预览可见，未登录浏览器访问 `/api/media/{id}` 返回 404；发布后返回 302 并正常显示；
② 私有文件在未登录的浏览器中访问 `/api/media/{id}` 返回 404；
③ 把 `.txt` 改名为 `.png` 上传会被拒绝；
④ 完成上传后，用浏览器开发者工具重放那次对 OSS 的 POST（同一授权、换成别的同大小内容），文章中的图片不变；
⑤ 连续点击“设为私有 / 设为公开”几次都成功，OSS 控制台中最终只剩一个 `public/…` 对象（旧对象在 10 分钟内被清理）。

## 5. 生产部署（HTTPS）

1. 准备一台安装了 Docker 的服务器，把域名解析到服务器。
2. 修改 `.env`：

   ```
   SPRING_PROFILES_ACTIVE=prod
   SESSION_COOKIE_SECURE=true
   NGINX_CONF=nginx-https.conf
   BLOG_SITE_BASE_URL=https://blog.example.com
   ```

3. 把 `deploy/nginx-https.conf` 中的 `blog.example.com` 替换为你的域名。
4. 申请证书（以 certbot webroot 为例）。先用 HTTP 配置启动一次，让 ACME 校验目录可以访问：

   ```bash
   mkdir -p deploy/certs deploy/certbot-www
   docker run --rm -v "$PWD/deploy/certbot-www:/var/www/certbot" -v "$PWD/deploy/letsencrypt:/etc/letsencrypt" \
     certbot/certbot certonly --webroot -w /var/www/certbot -d blog.example.com
   cp deploy/letsencrypt/live/blog.example.com/fullchain.pem deploy/certs/
   cp deploy/letsencrypt/live/blog.example.com/privkey.pem deploy/certs/
   docker compose up -d
   ```

   续期后复制证书并执行 `docker compose exec nginx nginx -s reload`。
5. 安全组只开放 80/443。MySQL 和应用不映射端口，只在内部网络中通信。

运维要点：

- `prod` profile 会强制 Cookie 为 `Secure`（同时 `HttpOnly`、`SameSite=Lax`）。
- 健康检查 `/actuator/health` 只返回 `{"status":"UP"}`，Nginx 会对外屏蔽 `/actuator/`。
- 日志中不会出现密码、AccessKey 或签名 URL（OSS SDK 日志级别为 warn，签名地址只出现在 302 响应头中）。
- Nginx 对 `/api/auth/login` 限速（每分钟 10 次，突发 5 次）。请求体上限为 2 MB，Tomcat 表单上限同样为 2 MB。

### 5.1 小内存服务器：GitHub Actions 构建，服务器只运行镜像

`.github/workflows/images.yml` 在推送 `main` 或手动运行时，分别构建 Linux amd64 后端和前端镜像并发布到 GHCR。
使用仓库自带的 `GITHUB_TOKEN`，无需把服务器密码、数据库密码或 OSS 密钥添加到 Actions。
镜像名为 `ghcr.io/hust-changge/lcblog-app:<完整提交SHA>` 和 `ghcr.io/hust-changge/lcblog-nginx:<完整提交SHA>`。
两个任务都成功后才部署对应版本；只有一个任务成功的版本不要部署。

首次配置：

1. 提交并推送工作流、`compose.registry.yml` 和本文档，打开仓库 Actions，等待 **Build images** 的两个任务成功。
2. GHCR 首次发布的包默认私有。服务器使用 GitHub personal access token **classic**（只需 `read:packages`，且账号有包的读取权限）登录。
   在 GitHub Settings → Developer settings → Personal access tokens → Tokens (classic) 创建令牌，设置有效期。
   保持镜像私有即可，不需要改为公开。

在服务器执行（输入令牌时不会显示，也不会将令牌本身写入命令历史）：

```bash
read -rsp "GitHub read:packages token: " GHCR_TOKEN
echo
printf '%s' "$GHCR_TOKEN" | sudo docker login ghcr.io -u hust-changge --password-stdin
unset GHCR_TOKEN
```

正常使用 Git 代理拉取代码后，先确认当前提交对应的 Actions 已全部成功，再执行：

```bash
cd ~/lcblog-repo
git pull --ff-only
export IMAGE_TAG=$(git rev-parse HEAD)
sudo env IMAGE_TAG="$IMAGE_TAG" docker compose -f compose.yml -f compose.registry.yml config --quiet &&
sudo env IMAGE_TAG="$IMAGE_TAG" docker compose -f compose.yml -f compose.registry.yml pull app nginx &&
sudo env IMAGE_TAG="$IMAGE_TAG" docker compose -f compose.yml -f compose.registry.yml up -d --no-build --pull never --force-recreate app nginx
```

`sudo env` 显式传入镜像版本，避免 sudo 清除环境变量。`--no-build` 保证服务器不编译，`--pull never` 使用已下载的镜像。
保留服务器已有 `.env`、数据库密码、`lcblog` 项目名、MySQL 数据卷和证书目录。命令只拉取两个应用镜像；
现有部署无需重新下载 MySQL。如为全新部署，先运行 `sudo docker compose pull mysql`。

检查：

```bash
sudo env IMAGE_TAG="$IMAGE_TAG" docker compose -f compose.yml -f compose.registry.yml ps
sudo env IMAGE_TAG="$IMAGE_TAG" docker compose -f compose.yml -f compose.registry.yml logs --tail=80 app nginx
curl --noproxy '*' -fsS http://127.0.0.1/api/public/site
```

等 app 健康后验证页面。若仅重建了 app，Nginx 可能仍保留旧容器 IP，因此这里一起重建 app/nginx。
HTTPS 配置请使用对应 HTTPS 地址验证。Git 与 Docker 的出站代理分别配置，服务器仍需能下载 GHCR 镜像。

以后更新重复“拉取代码 → 等对应 Actions 成功 → pull → up”；不要再执行服务器端的 `docker compose build`。
回滚时把 `IMAGE_TAG` 改为以前构建成功的完整提交 SHA，重复 pull/up。回滚镜像不撤销数据库迁移，
涉及数据库结构的更新需单独检查兼容性。不要执行 `docker compose down -v`。

## 6. 备份与恢复

数据库保存文章正文和文件元数据，文件本体在 OSS。**备份需要传到服务器以外的地方。**

备份（在服务器上执行，建议用 cron 每天运行）：

```bash
mkdir -p backups
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines -uroot -p"$MYSQL_ROOT_PASSWORD" lcblog' \
  | gzip > backups/lcblog-$(date +%F).sql.gz
# 传到异地，任选其一：
ossutil cp backups/lcblog-$(date +%F).sql.gz oss://your-backup-bucket/db/      # 另建一个私有备份 Bucket
# scp backups/lcblog-$(date +%F).sql.gz user@another-host:/backups/
find backups -name '*.sql.gz' -mtime +14 -delete
```

恢复：

```bash
docker compose up -d mysql
gunzip -c lcblog-2026-10-08.sql.gz | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" lcblog'
docker compose up -d
```

以上命令请在 Linux shell 中运行。在 Windows PowerShell 里用管道重定向会改变 dump 的编码。
OSS 文件建议开启 Bucket 版本控制或跨区域复制，作为文件层面的备份。定期在测试环境做一次恢复演练。

## 7. 测试

后端单元测试与接口测试（不需要数据库、Docker 或 OSS，需要 JDK 17）：

```bash
mvn -f blog-backend/pom.xml test
```

真实 MySQL 集成测试（`MediaLifecycleMySqlTest`，未提供参数时自动跳过）。需要一个**专用的空库**，测试会清空其中的文章与文件表：

```sql
CREATE DATABASE lcblog_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON lcblog_it.* TO 'lcblog'@'localhost';
```

```powershell
mvn -f blog-backend/pom.xml test "-Dlcblog.it.db-url=jdbc:mysql://127.0.0.1:3306/lcblog_it?serverTimezone=Asia/Shanghai" "-Dlcblog.it.db-user=lcblog" "-Dlcblog.it.db-password=<密码>"
```

（PowerShell 中 `-D` 参数需要加引号，否则会在 `.` 处被拆开。）

| 测试 | 覆盖内容 |
| --- | --- |
| `ApiSecurityTest` | 未登录写文章、申请/确认上传、私有下载、管理相册一律 401 JSON 且不重定向；未声明路径默认拒绝；登录成功/失败返回 JSON；无 CSRF 或伪造令牌 403；模拟浏览器“读 Cookie → 回传请求头”的 CSRF 流程；草稿 slug 404；正文只返回净化 HTML；管理员经 `/api/media/{id}` 预览草稿图片 |
| `PublicQueryGuardTest` | 所有访客 SQL（文章、分类、标签、文件、相册）都固定 `PUBLISHED` / `READY + PUBLIC + hidden = FALSE`；草稿文章的公开附件对访客不可见 |
| `ArticleServiceTest` | 发布与取消发布；文章与标签在同一事务中替换，失败向上传播以触发回滚；slug 校验 |
| `MarkdownRendererTest` | `<script>`、事件属性、`javascript:`、`data:` 等恶意内容不会执行；目录与代码语言标记 |
| `MediaServiceTest` | 完成确认拒绝缺失对象、大小不符、内容与扩展名不符、非本人签发的记录；核验后重复使用上传授权不能替换 READY 文件；数据库失败时撤销最终副本；可见性切换使用新 key、数据库失败保留原文件、并发切换的失败方不删除胜者的对象；HTML/SVG 被拒绝；被使用的文件拒绝删除 |
| `MediaLifecycleMySqlTest`（真实 MySQL + Flyway + 完整 Spring 上下文，OSS 用内存替身） | 真实登录 Session + CSRF；草稿保存 → 上传图片 → 访客 404 / 管理员可预览 → 发布 302 且出现在附件列表 → 取消发布 404 → 删除草稿后外键置空、`hidden` 生效、访客仍 404、文件本体保留 → 设为独立素材后公开；删除已发布文章不隐藏附件；重复上传不影响 READY 文件、暂存 key 在授权过期后才被清理；PUBLIC/PRIVATE 在清理前连续切换 4 次，清理只删旧对象；私有附件对访客 404、管理员可下载；删除保护识别正文引用与附件关联、`/api/media/{id}0` 不误判；文章与标签事务整体回滚 |

前端：

```bash
cd blog-frontend/kuailemao-blog && npm run build     # vue-tsc 类型检查 + 生产构建
cd blog-frontend/kuailemao-admin && npm run type-check && npm run build    # build 也会先执行 vue-tsc
```

## 8. 已知限制

- 前台是 SPA：搜索引擎能抓取 `sitemap.xml` 中的地址，但页面内容依赖 JavaScript 渲染，SEO 不如服务端渲染。
  文章的 `<title>` 与 description 会在前端动态设置。
- 搜索使用 `LIKE`，只匹配标题和摘要，不支持分词。文章数量较大时可改用 MySQL FULLTEXT（ngram）。
- 归档、站点地图、相邻文章一次查询全部已发布文章，适合个人博客的规模（几千篇以内）。
- OSS 表单签名使用 SDK 的 V1 签名（`calculatePostSignature`），OSS 目前仍支持；如需 V4，可替换 `AliyunOssGateway` 中的这一处实现。
- 只有登录接口有限速，没有账号锁定或二次验证。
- 引用检查只识别正文中 `/api/media/{id}` 格式的引用；如果手动写了 OSS 原始地址，不会被识别。
- 文件在公开和私有之间切换时，需要在两个 Bucket 之间复制对象；旧对象在提交后立即尝试删除，失败的由清理任务重试。
- 完成确认会多一次 OSS 内部复制（同 Bucket，不经过应用服务器），大文件确认会慢一些。
- 极端情况下（撤销时数据库也不可用）会留下一个未被记录引用的孤立对象，需要按 `upload/`、`public/`、`private/` 前缀
  对照数据库人工清理；这是为了绝不误删当前有效文件而做的取舍。
- 一个文件只能关联一篇文章。同一张图片在多篇文章中复用时，以关联的那篇文章的发布状态为准。
- 文件管理页最多列出最近一页文章作为可关联选项。
- 前台背景图来自上游仓库、每张约 4 MB，建议替换并压缩（见 `blog-frontend/REUSE.md`）。
