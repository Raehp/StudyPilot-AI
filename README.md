# StudyPilot AI · 高校学习科研智能工作台

> 面向高校学生与科研新手的一站式学习科研工作台：论文阅读解析、科研知识库、AI 智能问答（RAG）、学习计划、智能出题与在线练习。
> 本项目为参赛项目，按里程碑 M1–M7 逐步交付，**所有功能均为真实实现，不提供伪造数据或空实现**。

## 功能模块与里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M1 | 基础框架：11 表数据库设计、JWT + Redis 认证（注册/登录/登出/单会话）、统一响应与异常处理、健康检查、前端脚手架与登录注册 | ✅ 已完成 |
| M1+ | 前端 UI v2：9 大页面（iOS 玻璃拟态风格、明暗主题、响应式布局），未接入模块展示明确的空状态与里程碑标签 | ✅ 已完成 |
| M2 | 科研知识库：知识库 CRUD、PDF 上传与文件校验、PDFBox 异步解析、解析状态管理 | ⏳ 待开发 |
| M3 | AI 论文分析：通义千问 API 接入、摘要生成、结构化分析、AI 结果持久化 | ⏳ 待开发 |
| M4 | 向量化管线：文本清洗、Chunk 切片、DashScope Embedding、向量持久化（MySQL JSON） | ⏳ 待开发 |
| M5 | RAG 智能问答：向量检索（JVM 内余弦相似度 Top-K）、SSE 流式输出、引用溯源、历史会话 | ⏳ 待开发 |
| M6 | 学习辅助：学习计划生成、任务管理、智能出题、在线练习、成绩记录 | ⏳ 待开发 |
| M7 | 系统整合与比赛交付：工作台真实统计、全链路测试、安全检查、部署与演示材料 | ⏳ 待开发 |

未开发模块的前端页面仅展示页面结构与诚实的空状态（"暂无数据 / 待接入真实统计"），相关操作按钮禁用并标注所属里程碑。

## 技术栈

**后端** `studypilot-backend`
- Java 17 · Spring Boot 3.3.5 · Maven
- MyBatis-Plus 3.5.7 · MySQL 8.0（逻辑删除、分页插件）
- Redis（Lettuce 连接池）：JWT 单会话令牌存储
- jjwt 0.12.6 · Spring Security Crypto（BCrypt 密码哈希）
- Spring Boot Actuator · Lombok
- 预留：Spring AI Alibaba / DashScope（qwen-plus + text-embedding-v3，M3 启用）

**前端** `studypilot-frontend`
- Vue 3 + TypeScript（strict）· Vite 5
- Element Plus（中文语言包 + 暗色 css-vars）· Pinia · Vue Router（登录守卫）
- Axios 统一封装（Result 解包、401 自动跳转登录）
- ECharts（echarts/core 按需引入）
- iOS 风格玻璃拟态设计：CSS 变量明暗双主题、backdrop-filter、极光渐变背景、≤900px 抽屉式导航

**架构决策**
- 向量存储：MySQL JSON 字段 + JVM 内余弦相似度 Top-K（刻意不引入向量数据库），Redis 缓存热点知识库向量
- 认证语义：单会话 JWT —— 新登录会使旧令牌失效
- 所有密钥（数据库密码、DashScope API Key、JWT Secret）仅通过**后端环境变量**注入，严禁写入前端或提交到仓库

## 目录结构

```
StudyPilot-AI/
├── sql/init.sql                 # 11 张表完整建表脚本（utf8mb4，无物理外键）
├── docs/                        # 截图等文档资产
├── studypilot-backend/          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/studypilot/
│       ├── common/              # Result / ResultCode / 全局异常处理
│       ├── config/              # CORS / MyBatis-Plus / Redis / Web / Jackson
│       ├── security/            # JwtUtil / JwtInterceptor / UserContext
│       ├── controller/          # Auth / User / Health
│       ├── service/ + impl/     # 用户业务
│       ├── mapper/ entity/ dto/ vo/
│       └── resources/application.yml
└── studypilot-frontend/         # Vue3 前端
    └── src/
        ├── api/                 # 接口层（与页面分离）
        ├── components/          # common 通用组件 + business 业务组件
        ├── layouts/             # 主布局 + 侧边导航
        ├── router/ stores/      # 路由守卫 / Pinia 状态
        ├── styles/              # iOS 主题 CSS 变量
        ├── utils/               # Axios 封装 / token 管理
        └── views/               # 9 大页面
```

## 快速开始

### 环境要求

- JDK 17、Maven 3.9+
- MySQL 8.0（本地实例，创建数据库 `studypilot`）
- Redis（本地实例，默认 6379）
- Node.js 18+（建议 20/24）

### 1. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS studypilot DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p studypilot < sql/init.sql
```

### 2. 启动后端

敏感配置一律走环境变量（`application.yml` 中只有占位符）：

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | 数据库连接 | localhost / 3306 / studypilot |
| `MYSQL_USERNAME` | 数据库用户名 | root |
| `MYSQL_PASSWORD` | 数据库密码 | **必填，无默认值** |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | localhost / 6379 / 空 |
| `JWT_SECRET` | JWT 签名密钥 | 开发默认值，**生产必须覆盖** |
| `DASHSCOPE_API_KEY` | 通义千问 Key（M3 起） | 仅后端读取 |

```bash
cd studypilot-backend
# Linux / macOS / Git Bash
MYSQL_PASSWORD=你的密码 mvn spring-boot:run
# Windows PowerShell
$env:MYSQL_PASSWORD="你的密码"; mvn spring-boot:run
```

后端启动于 `http://localhost:8080`，健康检查：

```bash
curl http://localhost:8080/api/health
# {"code":200,"message":"success","data":{"application":"StudyPilot AI","status":"UP"}}
```

> IDE（IntelliJ IDEA）启动时请在 Run Configuration 的 Environment variables 中配置 `MYSQL_PASSWORD` 等变量，不要将真实密码写回 `application.yml`。

### 3. 启动前端

```bash
cd studypilot-frontend
npm install
npm run dev        # http://localhost:5173，/api 代理到后端 8080
```

可选：创建 `.env.development`（已被 gitignore，不会提交）写入 `VITE_API_BASE_URL=/api`；缺省时 Axios 自动回退到 `/api`。

生产构建与类型检查：

```bash
npx vue-tsc --noEmit   # TypeScript 检查
npm run build          # 产物输出 dist/
```

### 4. 演示账号

| 用户名 | 密码 |
|--------|------|
| demo | demo123456 |
| lisi | test123456 |
| wangwu | test123456 |

也可在注册页自行创建账号（用户名 3–32 位字母/数字/下划线，密码 6–32 位）。

## API 概览（M1）

统一响应结构：`{ "code": 200, "message": "success", "data": ... }`；错误码：400 参数错误 / 401 未认证或令牌失效 / 403 无权限 / 404 不存在 / 429 限流 / 500 服务器错误 / 5001 业务错误。

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册（BCrypt 存储密码） | 否 |
| POST | `/api/auth/login` | 登录，返回 JWT（单会话，旧令牌失效） | 否 |
| POST | `/api/auth/logout` | 登出，删除 Redis 令牌 | 是 |
| GET | `/api/users/me` | 当前用户信息 | 是 |
| GET | `/api/health` | 健康检查 | 否 |
| GET | `/actuator/health` | Actuator 健康端点 | 否 |

认证方式：请求头 `Authorization: Bearer <token>`。

## 数据库设计

11 张表（详见 [sql/init.sql](sql/init.sql)）：`sys_user`、`biz_knowledge_base`、`biz_document`、`biz_document_chunk`（embedding JSON）、`biz_conversation`、`biz_chat_message`（citations JSON）、`biz_study_plan`、`biz_study_task`、`biz_quiz`、`biz_quiz_question`、`log_ai_call`。

约定：bigint unsigned 自增主键、`create_time`/`update_time`/`deleted` 逻辑删除三件套、utf8mb4_unicode_ci、无物理外键（应用层保证关联）。

## 安全说明

- 密码 BCrypt 哈希存储；JWT 令牌存 Redis，支持登出即失效与单会话互踢
- 所有密钥仅从环境变量读取；`.env*`、`application-local.yml`、`target/`、`node_modules/`、`dist/` 均被 gitignore
- 前端不包含任何 AI API Key；AI 调用一律经后端代理（M3 起）
- CORS 当前为开发配置（`/api/**` 允许任意来源），部署时需收紧

## 已知事项 / 取舍

- Element Plus 采用全量引入，vendor chunk > 500 kB（gzip 约 342 kB）——已接受的取舍；ECharts 已按需引入
- 工作台统计、论文阅读、知识库、问答、计划、出题页面当前为诚实空状态，随 M2–M6 逐步接入真实数据
- 仓库名 `StudyPolit-AI` 与本地目录 `StudyPilot-AI` 拼写差异为历史原因，暂未统一

## License

参赛项目，版权归项目团队所有。