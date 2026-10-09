# HTTP 与 HTTPS 共用的路由规则（由 nginx-http.conf / nginx-https.conf include）。

# 前后台页面的安全响应头。Element Plus / Ant Design Vue 会注入内联样式，因此 style-src 需要 'unsafe-inline'；
# 脚本只允许同源。img-src / connect-src 允许 https: 以访问 OSS 签名地址与浏览器直传。
set $csp "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data: blob:; font-src 'self' data:; connect-src 'self' https:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;

location /.well-known/acme-challenge/ {
    root /var/www/certbot;     # 申请 HTTPS 证书时使用
}

# 健康检查只供容器内部使用
location /actuator/ {
    return 404;
}

location = /api/auth/login {
    limit_req zone=login burst=5 nodelay;
    proxy_pass http://app:8080;
}

location /api/ {
    proxy_pass http://app:8080;
}

# SEO 文件由后端按已发布文章动态生成
location = /sitemap.xml {
    proxy_pass http://app:8080;
}

location = /robots.txt {
    proxy_pass http://app:8080;
}

# 后台 SPA：/admin/ 下任意路径刷新都回退到后台 index.html
location ^~ /admin/ {
    root /usr/share/nginx/html;
    add_header Content-Security-Policy $csp always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header Strict-Transport-Security "max-age=31536000" always;   # HTTP 下浏览器会忽略
    add_header X-Robots-Tag "noindex, nofollow" always;
    try_files $uri $uri/ /admin/index.html;
}

location = /admin {
    return 301 /admin/;
}

# 带 hash 的构建产物可以长期缓存
location ~* ^/(js|css|assets|svg|png|jpg|jpeg|webp|woff2?|ttf)/ {
    root /usr/share/nginx/html/blog;
    add_header Cache-Control "public, max-age=31536000, immutable";
    try_files $uri =404;
}

# 前台 SPA：/posts/xxx 等地址直接打开或刷新时回退到 index.html，由前端路由渲染
location / {
    root /usr/share/nginx/html/blog;
    add_header Content-Security-Policy $csp always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header Strict-Transport-Security "max-age=31536000" always;   # HTTP 下浏览器会忽略
    add_header Cache-Control "no-cache" always;
    try_files $uri $uri/ /index.html;
}
