# StudyPilot AI 已知问题与取舍（KNOWN-ISSUES）

> 诚实记录当前系统的已知限制、待办与刻意取舍，不掩盖未完成项。日期 2026-11-18。

## 一、安全相关

### K1 · JWT 开发默认密钥（部署期强制覆盖）
- 现状：`application.yml` 的 `jwt.secret` 含开发默认值 `StudyPilot-AI-Dev-Secret-Key-Change-In-Production-2026`，仅供本地开发。
- 风险：若生产未覆盖，令牌可被伪造。
- 处置：部署通过 `.env` 的 `JWT_SECRET` 强制覆盖；`docker-compose.yml` 用 `${JWT_SECRET:?...}` 必填校验，缺失则拒绝启动。安全检查项 F3 已在部署期闭环。
- 状态：**部署期强制加固**（代码默认值保留以便开发，生产必须覆盖）。

### K2 · CORS 宽松（开发配置）
- 现状：`CorsConfig` 对 `/api/**` 设 `allowedOriginPatterns("*")` + `allowCredentials(true)`。
- 影响：生产经 Nginx 同源反代时不触发跨域，风险被缓解；若后端端口直接对外暴露，则任意源可跨域携带凭证。本系统用 Bearer 令牌（非 Cookie 会话），CSRF 面较小。
- 建议：生产如直接暴露后端，应收紧 `allowedOriginPatterns` 为可信前端域名。属配置变更，未在本轮擅自修改。

### K3 · 限流按代理 IP 桶（反代场景）
- 现状：M7-3 限流器用 `request.getRemoteAddr()`，不信任 `X-Forwarded-For`（防伪造）。
- 影响：经 Nginx 反代后，后端看到的来源 IP 为 Nginx 容器地址，所有客户端共享 auth/ai/upload 桶（如登录 10 次/60s 为全站合计）。
- 评估：比赛演示（少量用户）不受影响。若需按真实客户端 IP 限流，须引入可信代理 XFF 解析（安全代码变更，需另行确认）。
- 状态：已知取舍，未擅自修改。

### K4 · Redis 默认无密码
- 现状：Compose 中 Redis 仅在内部 docker 网络运行、不发布端口，默认无密码；`REDIS_PASSWORD` 预留。
- 建议：生产如需暴露 Redis 应启用鉴权。
- 注意：开发环境 Redis 与其他应用共享，运维**严禁 FLUSHALL**，仅可清理 `studypilot:*` 键。

## 二、功能未完成（诚实标注，未伪造完成）

### K5 · 个人资料修改 / 修改密码 / 登录设备管理
- 现状：个人设置页展示来自 `/users/me` 的真实资料；资料修改、修改密码、设备管理前端标注"暂未开放/后续开放"，后端无对应写接口。
- 状态：**尚未实现**，明确标注，不标记为完成。

## 三、性能与架构取舍

### K6 · 向量检索为内存 brute-force（刻意不引入向量数据库）
- 现状：按「用户 + 知识库」范围 SQL 预过滤（`idx_user_kb`）后 JVM 余弦 Top-K，无常驻索引、无向量缓存。
- 实测：demo KB N=288、D=1024，检索平均约 437ms（主导为 Embedding API 往返），余弦计算量可忽略。
- 阈值：切片量约 >1 万需评估 ANN 索引（如 pgvector/专用向量库）。
- 状态：**替换向量存储架构需用户确认**，M7 未变更。

### K7 · 待批准的索引优化
- 建议：`biz_document(user_id,status)`、`biz_quiz(user_id,create_time)` 等复合索引可进一步优化统计/列表。
- 状态：属 DB 迁移，需用户批准，M7 未擅自实施。详见 M7-performance-report.md。

### K8 · 无 @Scheduled 自动恢复 Reaper
- 现状：卡住的异步任务靠 Redis 锁 TTL 到期 + 用户重试恢复，无定时清理器。
- 评估：当前规模足够；引入定时 Reaper 属架构增强，需确认。

## 四、前端与构建

### K9 · Vendor chunk 体积
- 现状：Element Plus 全量引入，chunk 1086.50 kB（gzip 342.54 kB）；echarts 500.62 kB（gzip 168.24 kB），超 500 kB 警告阈值。
- 处置：已 manualChunks 独立分包；业务视图均 <100 kB。属已接受取舍（如需可改 Element Plus 按需引入）。

### K10 · 响应式运行时移动端截图未做
- 现状：`@media` 断点（900/1100/700）+ 抽屉导航代码完整；隐藏浏览器面板无法真实缩放视口，未做运行时移动端截图验证。
- 状态：代码层面响应式完整，运行时移动端视觉验证待补。

## 五、配置整洁性

### K11 · application.yml 中 rag 配置块缩进异常（历史遗留）
- 现状：`rag:` 块在 YAML 中位于 `logging:` 之下（缩进异常），故 `studypilot.rag.*` 未通过该块生效；RAG 实际使用 `RagProperties` 的默认值（top-k 5 / min-score 0.5 / sse-timeout 180s 等），与预期一致，功能正常。
- 影响：直接修改该 YAML rag 块不会生效（需改 RagProperties 默认或修正缩进）。
- 状态：功能不受影响；属配置整洁性问题，修正缩进需回归验证 RAG，未在本轮擅自改动。

## 六、部署

### K12 · 本地未执行 Docker 部署
- 现状：开发机（Windows）未安装 Docker，本轮交付部署产物（Dockerfile×2 / nginx.conf / docker-compose.yml / .env.example）并通过 YAML 结构校验，未实际 `docker compose up`。
- 状态：实际容器部署需在目标主机执行后据实记录，**不虚构部署地址与运行指标**。见 DEPLOYMENT-GUIDE.md。

## 七、运维提醒

- **DashScope API Key 轮换**：自 M3 起待轮换；Key 仅存于仓库外 `E:/AI-Qoder-Pro/.secrets/dashscope.key`，仅后端环境变量注入，绝不提交 Git/前端/日志。建议比赛后尽快轮换。
- Git 安全：禁止 force push / reset --hard / 删除历史 / 提交真实 Key / 提交 .env / 未经确认覆盖远程；M7 变更提交需用户确认。