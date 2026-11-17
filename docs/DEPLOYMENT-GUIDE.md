# StudyPilot AI 部署指南（DEPLOYMENT-GUIDE）

> 阶段：M7 系统整合与比赛交付 · 子任务 M7-7
> 目标：用 Docker Compose 一键编排 Vue 前端 + Spring Boot 后端 + MySQL 8 + Redis 7。
> 重要：本项目的后端是 **Spring Boot 可执行 jar**，运行于容器中的 JRE17，**不是** Qoder Sites 静态托管环境；Qoder Sites 不能运行 Spring Boot 后端。

## 一、部署产物清单

| 文件 | 作用 |
|------|------|
| `docker-compose.yml` | 四服务编排（mysql / redis / backend / frontend），持久化卷、健康检查、启动依赖 |
| `.env.example` | 环境变量模板（复制为 `.env` 填真实值；`.env` 不提交 Git） |
| `studypilot-backend/Dockerfile` | 多阶段：Maven+JDK17 打包 → JRE17 运行 |
| `studypilot-backend/.dockerignore` | 排除 target/data/logs/.env 等 |
| `studypilot-frontend/Dockerfile` | 多阶段：Node20 构建 → Nginx 托管 SPA |
| `studypilot-frontend/.dockerignore` | 排除 node_modules/dist/.env 等 |
| `studypilot-frontend/nginx.conf` | SPA 路由回退 + `/api` 反向代理（含 SSE 关闭缓冲） |
| `sql/init.sql` | 全新部署的完整 schema（已含 M1–M6 全部表与字段） |

## 二、前置条件

- 一台安装 Docker Engine + Docker Compose v2 的主机（Linux 推荐）。
- 可访问 Maven Central / npm registry / Docker Hub（构建期拉取依赖与基础镜像）。
- 一个有效的 DashScope API Key（仅注入后端）。
- 本开发机（Windows）**未安装 Docker**，故本轮未在本地实际执行 `docker compose up`；以下命令与产物需在具备 Docker 的目标主机执行。部署结果以目标主机实际运行为准，不在此虚构部署地址。

## 三、密钥与配置（强制）

1. 复制模板：`cp .env.example .env`
2. 填写真实值：
   - `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD`：强密码。
   - `JWT_SECRET`：**必须**设置为强随机值（≥32 字节），覆盖代码中的开发默认密钥。这是安全检查项 **F3** 的部署期强制加固。生成示例：`openssl rand -base64 48`。
   - `DASHSCOPE_API_KEY`：真实 Key。
3. `docker-compose.yml` 对 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET`、`DASHSCOPE_API_KEY` 使用 `${VAR:?...}` 必填校验：缺失则 compose 直接报错拒绝启动，避免误用开发默认密钥。
4. 安全约束：
   - `.env` 已被根 `.gitignore` 忽略（`.env` / `.env.*`，仅放行 `.env.example`），**绝不提交 Git**。
   - 密钥仅注入后端容器环境变量；前端镜像与代码不含任何密钥。
   - 后端 `application.yml` 中 `MYSQL_PASSWORD` 无默认值、`DASHSCOPE_API_KEY` 默认空、`JWT_SECRET` 默认值仅供开发，生产由环境变量覆盖。
   - 日志不打印密钥（AI 客户端与拦截器均脱敏）。

## 四、启动 / 停止 / 重启

```bash
# 构建并后台启动（首次会拉取镜像、编译前后端、初始化数据库）
docker compose up -d --build

# 查看各服务状态与健康检查
docker compose ps

# 跟踪后端日志
docker compose logs -f backend

# 停止并移除容器（保留数据卷）
docker compose down

# 停止并清空数据卷（危险：删除数据库与上传文件，仅在确需重置时）
docker compose down -v

# 重启单个服务
docker compose restart backend
```

启动顺序由 `depends_on` + `condition: service_healthy` 保证：
MySQL 健康 → Redis 健康 → 后端启动并健康（`/api/health`）→ 前端启动。

访问入口：`http://<主机IP>:${FRONTEND_PORT:-80}`（前端 Nginx）。前端经 `/api` 反向代理到后端容器 `backend:8080`，后端与数据库端口默认不对外发布，仅存在于内部 docker 网络。

## 五、数据库初始化与迁移说明

- **全新部署**：MySQL 容器首次启动时，`docker-entrypoint-initdb.d/init.sql` 自动执行。`init.sql` 已是 **M1–M6 合并后的完整 schema**（12 张表，含 `need_ocr`、向量化字段、`biz_chat_message.status/error_msg`、计划/测验扩展字段、`idx_user_kb` 等索引）。
- **迁移脚本** `sql/migration-m2.sql … migration-m6.sql` 是开发期对已有库的**历史增量 ALTER**，其变更已并入 `init.sql`。**全新部署不要再执行这些迁移**，否则会因重复 ADD COLUMN 报错。
- **已有旧库升级**（非容器全新初始化场景）才按 m2→m3→m4→m5→m6 顺序执行迁移：
  ```bash
  mysql -uroot -p --default-character-set=utf8mb4 studypilot < sql/migration-m2.sql
  # …依次 m3 / m4 / m5 / m6
  ```
- 数据持久化：`mysql-data` 卷挂载 `/var/lib/mysql`，容器重建不丢数据。`init.sql` 仅在数据目录为空（首次）时执行。
- 字符集：MySQL 服务端 `utf8mb4 / utf8mb4_unicode_ci`；Windows 客户端导入须带 `--default-character-set=utf8mb4` 防中文乱码。

## 六、文件存储与持久化卷

| 卷 | 挂载点 | 内容 |
|----|--------|------|
| `mysql-data` | mysql:/var/lib/mysql | 数据库数据 |
| `redis-data` | redis:/data | Redis AOF 持久化（登录态/限流/分布式锁） |
| `uploads-data` | backend:/app/data/uploads | 上传 PDF 与解析结果 JSON |

后端 `STORAGE_DIR=/app/data/uploads`，上传文件以 UUID 重命名落盘，删除文档/知识库时级联物理清理。卷保证容器重建后文件不丢失。

## 七、健康检查

| 服务 | 检查 | 参数 |
|------|------|------|
| mysql | `mysqladmin ping -h 127.0.0.1 -uroot -p$MYSQL_ROOT_PASSWORD` | interval 10s / retries 10 / start_period 30s |
| redis | `redis-cli ping` | interval 10s / retries 5 / start_period 10s |
| backend | `curl -fsS http://localhost:8080/api/health` | interval 15s / retries 5 / start_period 60s |
| frontend | 依赖 backend 健康后启动 | — |

`/api/health` 为免鉴权健康端点，返回 `{"code":200,...,"status":"UP"}`。

## 八、日志与排查

```bash
docker compose logs -f backend     # 后端应用日志
docker compose logs -f mysql       # 数据库初始化/错误
docker compose logs -f frontend    # Nginx 访问/错误
docker compose ps                  # 健康状态（healthy / starting / unhealthy）
docker inspect studypilot-backend --format '{{json .State.Health}}'   # 健康检查明细
```

常见问题：
- 后端 `unhealthy`：多为数据库未就绪或密钥缺失。查 `logs backend`；确认 `.env` 的 `MYSQL_PASSWORD`/`JWT_SECRET`/`DASHSCOPE_API_KEY` 已填。
- 数据库初始化失败：查 `logs mysql`；若曾以错误密码初始化过卷，需 `docker compose down -v` 重置（会清空数据）后重试。
- AI 调用失败：确认 Key 有效与外网可达 DashScope；后端读超时 120s，SSE 180s。
- 上传 413：Nginx `client_max_body_size 35m` 与后端 multipart 35MB 已对齐；超限文件后端返回 400。
- 问答流式中断：Nginx 已 `proxy_buffering off` + `proxy_read_timeout 300s`，覆盖后端 SSE 180s。

## 九、部署期已知事项（诚实标注）

1. **F3 JWT 密钥**：代码默认密钥仅供开发；部署通过 `.env` 的 `JWT_SECRET` 强制覆盖，compose 必填校验兜底。
2. **限流按 IP 桶**：M7-3 限流器使用 `request.getRemoteAddr()`（不信任 X-Forwarded-For，防伪造）。经 Nginx 反代后，后端看到的来源 IP 为 Nginx 容器地址，故同一部署下所有客户端共享 auth/ai/upload 桶（如登录 10 次/60s 为全站合计）。比赛演示（少量用户）不受影响；若需按真实客户端 IP 限流，须引入可信代理 XFF 解析（属安全代码变更，需另行确认，未在本轮擅自修改）。
3. **Redis 无密码**：默认仅在内部 docker 网络运行、不发布端口；`REDIS_PASSWORD` 预留，生产如需暴露 Redis 应启用鉴权。
4. **向量检索架构**：按用户+知识库范围的内存余弦 brute-force（SQL 预过滤 `idx_user_kb`），无常驻索引。当前规模（demo KB 288 切片）检索平均约 437ms（主导为 Embedding API 往返）。切片量显著增长（约 >1 万）需评估 ANN 索引，**替换向量存储架构需用户确认**，本轮未变更。
5. **本地未执行 docker**：开发机无 Docker，本轮交付的是可运行产物与指南；实际部署结果需要在目标主机执行后据实记录，不虚构部署地址/指标。

## 十、验收建议（目标主机执行后）

1. `docker compose ps` 四服务均 healthy。
2. 浏览器访问前端 → 注册/登录 → 创建知识库 → 上传 PDF → 解析 → AI 分析 → 向量化 → RAG 问答（含引用）→ 学习计划 → 智能出题 → 工作台统计，全链路真实落库。
3. 重启容器（`docker compose restart`）后数据与上传文件仍在（卷持久化）。
4. 确认 `.env` 未进入 Git（`git status` 不显示 `.env`）。