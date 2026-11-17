# StudyPilot AI · 高校学习科研智能工作台

> 面向高校学生与科研新手的一站式学习科研工作台：论文阅读解析、科研知识库、AI 智能问答（RAG）、学习计划、智能出题与在线练习、真实数据工作台。
> 参赛项目，按里程碑 M1–M7 交付。**所有功能均为真实实现，接真实 MySQL / Redis / DashScope，不提供伪造数据或空实现。**

## 功能模块与里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M1 | 基础框架：数据库设计、JWT + Redis 认证（注册/登录/登出/单会话）、统一响应与异常处理、健康检查、前端脚手架与登录注册 | ✅ 已完成 |
| M1+ | 前端 UI：iOS 玻璃拟态风格、明暗主题、响应式布局、通用组件 | ✅ 已完成 |
| M2 | 科研知识库：知识库 CRUD、PDF 上传与文件校验、PDFBox 异步解析、解析状态管理 | ✅ 已完成 |
| M3 | AI 论文分析：通义千问 qwen-plus 接入、摘要生成、12 项结构化分析、结果持久化 | ✅ 已完成 |
| M4 | 向量化管线：文本清洗、Chunk 切片、DashScope text-embedding-v3（1024 维）、向量持久化（MySQL JSON）、余弦检索 | ✅ 已完成 |
| M5 | RAG 智能问答：向量检索 Top-K、SSE 流式输出、引用溯源、多轮历史会话 | ✅ 已完成 |
| M6 | 学习辅助：AI 学习计划生成、任务打卡与进度、智能出题、在线练习、AI 判分与错题回顾 | ✅ 已完成 |
| M7 | 系统整合与比赛交付：工作台真实统计、全链路联调、安全检查与修复、性能核查、前端优化、自动化测试、Docker 部署、交付文档与演示 | ✅ 已完成 |

尚未实现的次要功能（个人资料修改、修改密码、登录设备管理）在前端诚实标注"暂未开放"，不伪造完成状态。

## 技术栈

**后端** `studypilot-backend`
- Java 17 · Spring Boot 3.3.5 · Maven
- MyBatis-Plus 3.5.7 · MySQL 8.0（逻辑删除 `deleted`、分页插件 maxLimit=500）
- Redis（Lettuce 连接池）：JWT 单会话令牌、分布式锁、接口限流计数
- jjwt 0.12.6 · Spring Security Crypto（BCrypt 密码哈希）
- Apache PDFBox 3.0.3（PDF 全文解析，保留页码）
- DashScope OpenAI 兼容端点（封装 RestClient 直连，**未引入 Spring AI**，因 Spring AI Alibaba GA 需 Boot 3.4+）：qwen-plus（分析/RAG/计划/出题/判分）+ text-embedding-v3（1024 维，批 ≤10）
- 5 个隔离异步线程池（解析/分析/向量化/对话/计划出题），AbortPolicy + 优雅停机
- Spring Boot Actuator · Lombok

**前端** `studypilot-frontend`
- Vue 3 + TypeScript（strict）· Vite 5
- Element Plus（中文语言包 + 暗色 css-vars）· Pinia · Vue Router（登录守卫）
- Axios 统一封装（Result 解包、401 自动跳转登录、429/超时友好提示）
- ECharts（echarts/core 按需引入 Bar/Line）· marked + DOMPurify（Markdown 安全渲染）
- iOS 风格玻璃拟态设计：CSS 变量明暗双主题、backdrop-filter、极光渐变背景、≤900px 抽屉式导航

**架构决策**
- 向量存储：MySQL JSON 字段 + 按「用户 + 知识库」范围的 JVM 内余弦相似度 Top-K（SQL 预过滤 `idx_user_kb`）。**刻意不引入向量数据库，无常驻索引、无向量缓存**；每次检索实时计算。当前规模性能良好，切片量显著增长需评估 ANN（变更需确认）。
- 认证语义：单会话 JWT —— 新登录使旧令牌失效。
- 所有密钥（数据库密码、DashScope API Key、JWT Secret）仅通过**后端环境变量**注入，严禁写入前端、提交仓库或打印日志。

## 目录结构

```
StudyPilot-AI/
├── sql/
│   ├── init.sql                 # 12 张表完整建表脚本（M1–M6 合并 schema，utf8mb4，无物理外键）
│   └── migration-m2..m6.sql     # 开发期历史增量迁移（已并入 init.sql，全新部署勿重复执行）
├── docker-compose.yml           # MySQL + Redis + 后端 + 前端 编排
├── .env.example                 # 部署环境变量模板（复制为 .env，不提交 Git）
├── docs/                        # 交付文档与里程碑报告
├── studypilot-backend/          # Spring Boot 后端
│   ├── Dockerfile
│   └── src/main/java/com/studypilot/
│       ├── common/              # Result / ResultCode / 全局异常处理 / VectorMath
│       ├── config/              # CORS / MyBatis-Plus / Redis / Web / Async / RateLimitProperties
│       ├── security/            # JwtUtil / JwtInterceptor / UserContext / RateLimitInterceptor
│       ├── controller/          # Auth/User/Health/KnowledgeBase/Document/Paper/Vector/Chat/StudyPlan/StudyTask/Quiz/Dashboard
│       ├── service/ + impl/     # 业务服务 + DashScope 客户端 + Prompt 构造器 + 校验器
│       ├── mapper/ entity/ dto/ vo/
│       └── resources/application.yml
└── studypilot-frontend/         # Vue3 前端
    ├── Dockerfile · nginx.conf
    └── src/
        ├── api/                 # 接口层（与页面分离）
        ├── components/          # common 通用组件 + business 业务组件
        ├── layouts/             # 主布局 + 侧边导航
        ├── router/ stores/      # 路由守卫 / Pinia 状态
        ├── styles/              # iOS 主题 CSS 变量
        ├── utils/               # Axios 封装 / token 管理
        └── views/               # 工作台/论文阅读/知识库/问答/计划/出题/设置/登录/注册
```

## 快速开始（本地开发）

### 环境要求
- JDK 17、Maven 3.9+、MySQL 8.0、Redis、Node.js 18+（建议 20/24）

### 1. 初始化数据库
```bash
mysql -u root -p --default-character-set=utf8mb4 < sql/init.sql
```
`init.sql` 已含 M1–M6 完整 schema（12 表）。全新部署不要再执行 migration 脚本。

### 2. 启动后端
敏感配置一律走环境变量（`application.yml` 中只有占位符）：

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | 数据库连接 | localhost / 3306 / studypilot |
| `MYSQL_USERNAME` | 数据库用户名 | root |
| `MYSQL_PASSWORD` | 数据库密码 | **必填，无默认值** |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | localhost / 6379 / 空 |
| `JWT_SECRET` | JWT 签名密钥 | 开发默认值，**生产必须覆盖** |
| `DASHSCOPE_API_KEY` | 通义千问 Key | 仅后端读取，默认空 |
| `STORAGE_DIR` | 上传文件目录 | ./data/uploads |
| `RATE_LIMIT_ENABLED` | 接口限流开关 | true |

```bash
cd studypilot-backend
MYSQL_PASSWORD=你的密码 DASHSCOPE_API_KEY=你的Key mvn spring-boot:run
```
后端启动于 `http://localhost:8080`，健康检查：
```bash
curl http://localhost:8080/api/health
# {"code":200,"message":"success","data":{"application":"StudyPilot AI","status":"UP"}}
```

### 3. 启动前端
```bash
cd studypilot-frontend
npm install
npm run dev        # http://localhost:5173，/api 代理到后端 8080
```
类型检查与生产构建：
```bash
npm run typecheck   # vue-tsc --noEmit
npm run build       # 产物输出 dist/
```

### 4. Docker 一键部署
见 [docs/DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md)：
```bash
cp .env.example .env   # 填入真实密钥（JWT_SECRET 必须覆盖）
docker compose up -d --build
```

### 5. 演示账号
| 用户名 | 密码 |
|--------|------|
| demo | demo123456 |
| lisi | test123456 |
| wangwu | test123456 |

也可在注册页自行创建账号（用户名 3–32 位字母/数字/下划线，密码 6–32 位）。演示流程见 [docs/DEMO-GUIDE.md](docs/DEMO-GUIDE.md)。

## 交付文档

| 文档 | 内容 |
|------|------|
| [docs/SYSTEM-ARCHITECTURE.md](docs/SYSTEM-ARCHITECTURE.md) | 系统架构、模块、数据流、异步与检索设计 |
| [docs/DATABASE-DESIGN.md](docs/DATABASE-DESIGN.md) | 12 张表结构、索引、关系与约定 |
| [docs/API-DOCUMENTATION.md](docs/API-DOCUMENTATION.md) | 全部 REST 接口、统一响应、错误码、认证 |
| [docs/DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md) | Docker Compose 部署、密钥、初始化、运维 |
| [docs/TEST-REPORT.md](docs/TEST-REPORT.md) | 自动化测试与真实联调结果 |
| [docs/DEMO-GUIDE.md](docs/DEMO-GUIDE.md) | 5–8 分钟比赛演示流程 |
| [docs/KNOWN-ISSUES.md](docs/KNOWN-ISSUES.md) | 已知问题与取舍 |
| [docs/M7-security-report.md](docs/M7-security-report.md) · [M7-performance-report.md](docs/M7-performance-report.md) · [M7-frontend-report.md](docs/M7-frontend-report.md) | M7 安全/性能/前端专项报告 |

## API 概览

统一响应结构：`{ "code": 200, "message": "success", "data": ... }`（**所有业务错误亦返回 HTTP 200，错误语义在 body.code**）。错误码：400 参数错误 / 401 未认证或令牌失效 / 403 无权限 / 404 不存在 / 405 方法不允许 / 429 限流 / 500 服务器错误 / 5001 AI 服务错误。认证：请求头 `Authorization: Bearer <token>`。完整接口见 [docs/API-DOCUMENTATION.md](docs/API-DOCUMENTATION.md)。

## 数据库设计

12 张表（详见 [sql/init.sql](sql/init.sql) 与 [docs/DATABASE-DESIGN.md](docs/DATABASE-DESIGN.md)）：`sys_user`、`biz_knowledge_base`、`biz_document`、`biz_document_chunk`（embedding JSON）、`biz_conversation`、`biz_chat_message`（citations JSON）、`biz_study_plan`、`biz_study_task`、`biz_quiz`、`biz_quiz_question`、`log_ai_call`、`biz_paper_analysis`。

约定：bigint unsigned 自增主键、`create_time`/`update_time`/`deleted` 逻辑删除三件套、utf8mb4_unicode_ci、无物理外键（应用层保证关联与级联清理）。

## 安全说明

- 密码 BCrypt 哈希存储；JWT 令牌存 Redis，登出即失效与单会话互踢。
- 用户数据严格隔离：跨用户访问返回 403/404（隐藏存在性）；列表互不可见。
- 上传四重校验（扩展名/MIME/PDF magic/大小 ≤30MB）+ UUID 重命名 + 路径穿越防护。
- 接口限流（Redis 固定窗口，按 IP+路由桶：登录/注册 10 次、AI 30 次、上传 30 次 / 60s）。
- RAG Prompt 注入隔离规则；AI 输出结构化校验。
- 所有密钥仅环境变量注入；`.env*`、`application-local.yml`、`target/`、`node_modules/`、`dist/`、`data/`、`*.jar`、`*.log` 均被 gitignore。
- 详见 [docs/M7-security-report.md](docs/M7-security-report.md)。

## 已知事项 / 取舍

- Element Plus 全量引入，vendor chunk > 500 kB（gzip 约 342 kB）——已接受取舍；ECharts 按需引入。
- 向量检索为按用户+知识库范围的内存余弦 brute-force，无常驻索引；大规模需评估 ANN（变更需确认）。
- 经 Nginx 反代后限流按代理 IP 桶（详见 KNOWN-ISSUES）。
- 个人资料修改/修改密码/设备管理暂未开放。
- 详见 [docs/KNOWN-ISSUES.md](docs/KNOWN-ISSUES.md)。

## License
参赛项目，版权归项目团队所有。