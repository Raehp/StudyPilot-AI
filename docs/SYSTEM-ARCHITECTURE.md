# StudyPilot AI 系统架构（SYSTEM-ARCHITECTURE）

> 本文档与实际代码保持一致。后端 Spring Boot 3.3.5 / Java 17，前端 Vue3 + TS + Vite5，数据 MySQL 8 + Redis，AI 为 DashScope（qwen-plus / text-embedding-v3）。

## 一、总体架构

```
┌─────────────────────────────────────────────────────────────┐
│  浏览器（Vue3 SPA · Element Plus · Pinia · ECharts）          │
│  iOS 玻璃拟态 UI · 明暗主题 · 响应式 · Axios 统一封装          │
└───────────────┬─────────────────────────────────────────────┘
                │ /api（开发:Vite proxy / 生产:Nginx 反代）
                ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot 后端 :8080                                       │
│  拦截器链：RateLimitInterceptor → JwtInterceptor → Controller │
│  Controller → Service(+impl) → Mapper(MyBatis-Plus)           │
│  异步线程池 ×5 · DashScope RestClient 封装 · Prompt/校验器     │
└───────┬───────────────────┬───────────────────┬─────────────┘
        ▼                   ▼                   ▼
   MySQL 8（12 表）     Redis（令牌/锁/限流）   DashScope API
   业务数据+向量JSON    单会话JWT·分布式锁      qwen-plus / embedding-v3
        ▲
        │ 上传文件 UUID 落盘 + 解析 JSON
   本地存储 STORAGE_DIR（持久化卷）
```

生产部署为 Docker Compose 四服务：`frontend`(Nginx) → `backend`(JRE17 jar) → `mysql` / `redis`，详见 DEPLOYMENT-GUIDE.md。后端是 Spring Boot 可执行 jar，**不是**静态托管环境。

## 二、后端分层与模块

| 层 | 包 | 职责 |
|----|----|----|
| 接入层 | `controller` | 12 个控制器，REST 映射 `/api/**`，参数校验（jakarta validation） |
| 安全层 | `security` | JwtUtil（jjwt 0.12.6）、JwtInterceptor（解析令牌+Redis 校验+UserContext）、RateLimitInterceptor（Redis 固定窗口限流） |
| 业务层 | `service` + `service.impl` | 知识库/文档/解析/分析/切片/向量化/检索/会话/RAG/计划/任务/出题/判分/工作台统计 |
| AI 集成 | `service`（DashScope 客户端） | 封装 RestClient 直连 OpenAI 兼容端点；chatJson（json_object）、流式 SSE、Embedding 批量 |
| 持久层 | `mapper` + `entity` | MyBatis-Plus，`@TableLogic deleted`，LambdaQueryWrapper，分页插件 maxLimit=500 |
| 公共 | `common` | Result/ResultCode 统一响应、GlobalExceptionHandler、VectorMath（余弦+边界守卫） |
| 配置 | `config` | WebConfig（拦截器注册/CORS）、AsyncConfig（5 池）、MybatisPlusConfig、Redis、RateLimitProperties、StorageProperties |

### 控制器与职责
- `AuthController` `/api/auth`：注册/登录/登出
- `UserController` `/api/users`：当前用户
- `HealthController` `/api/health`：免鉴权健康检查
- `KnowledgeBaseController` `/api/knowledge-bases`：知识库 CRUD + 文档归属
- `DocumentController` `/api/documents`：上传/列表/详情/删除/解析/解析状态/全文内容
- `PaperController` `/api/papers`：AI 分析提交/结果/状态
- `VectorController`：向量化提交/状态、知识库语义检索
- `ChatController` `/api/chat`：会话 CRUD、历史消息、SSE 流式问答、取消
- `StudyPlanController` `/api/study-plans`：计划 CRUD、AI 生成、任务列表、进度
- `StudyTaskController` `/api/study-tasks`：任务状态/编辑
- `QuizController` `/api/quizzes`：出题生成、列表、错题、详情、提交判分、结果、删除
- `DashboardController` `/api/dashboard`：overview / document-statistics / learning-statistics / quiz-statistics

## 三、请求处理与安全链

1. **RateLimitInterceptor**（先于 JWT）：仅对 POST 写操作按 IP+路由桶限流（auth 10 / ai 30 / upload 30 每 60s）。Redis `INCR` + 首计数设 TTL；超限返回 HTTP200+code429。Redis 异常时**放行**（fail-open，记录 warn），避免限流组件故障阻断业务。
2. **JwtInterceptor**：解析 `Authorization: Bearer`，校验签名与 Redis 中 `studypilot:auth:token:{userId}` 是否一致（单会话），写入 UserContext；失败返回 HTTP200+code401。`/api/health` 与认证接口免鉴权。
3. **Controller → Service**：业务所有权校验。跨用户访问抛 403/404（文档/知识库用 404 隐藏存在性）。
4. **统一响应**：所有接口（含错误）返回 HTTP 200 + `{code,message,data}`；GlobalExceptionHandler 兜底（含 MaxUploadSizeExceededException→400）。

## 四、核心数据流

### 4.1 论文入库与向量化管线
```
上传 PDF（四重校验+UUID重命名+落盘）
  → biz_document(status=UPLOADED)
  → 异步 parseExecutor：PDFBox 解析全文（保留页码）→ 解析 JSON 落盘 + status=READY
  → 异步 analysisExecutor：qwen-plus 摘要+12项结构化分析 → biz_paper_analysis(result JSON) + document.summary/analysis
  → 异步 vectorExecutor：文本清洗 → ChunkService 切片（target 500/overlap 80）
       → biz_document_chunk → Embedding 批量（≤10/批，1024维）→ embedding JSON 入库
       → document.vector_status=SUCCESS, chunk_count/embedding_count
```
状态机：DocumentStatus UPLOADED0/PARSING1/CHUNKING2/EMBEDDING3/READY4/FAILED5；VectorStatus PENDING0/PROCESSING1/SUCCESS2/FAILED3。各异步任务用 Redis 分布式锁（`studypilot:{parse|analysis|vector|plan|quiz}:lock:`）防重入，锁 TTL 到期自动恢复（无 @Scheduled  Reaper）。线程池满 AbortPolicy → 状态回滚 + 释放锁 + 429。

### 4.2 RAG 问答流
```
POST /api/chat/sessions/{id}/messages (SSE)
  → 校验会话所有权 + 问题长度
  → 问题 Embedding → 按 user+kb 范围 SQL 预过滤(idx_user_kb) → JVM 余弦 Top-K（min-score 0.5）
  → RagPromptBuilder 组装【检索内容】+【历史】+【用户问题】（含注入隔离规则）
  → qwen-plus 流式生成 → SSE 逐块推送 → 终态持久化 biz_chat_message(content+citations JSON+status)
  → 引用含 documentId/documentName/chunkId/pageNum/originalContent/similarityScore
```
MessageStatus GENERATING0/SUCCESS1/FAILED2/STOPPED3；支持取消。SSE 超时 180s，空闲 60s。

### 4.3 学习计划与出题
- 计划：创建 → planQuizExecutor 异步 qwen-plus 生成逐日任务（可结合知识库检索）→ PlanOutputValidator 校验 → biz_study_task 批量入库；任务打卡重算 progress。
- 出题：generate → 基于知识库真实内容 qwen-plus 生成单选/多选/判断/简答 → QuizOutputValidator 校验 → biz_quiz_question；练习态隐藏答案；提交后客观题自动判分、简答题 AI 判分（pass-score 60）→ score(0–100) + 错题回顾。

### 4.4 工作台统计
DashboardService 直接聚合真实 MySQL（按 user_id 隔离）：overview 9 项计数、文档解析/向量化分布与上传趋势、学习计划进度与活动趋势、测验成绩分布/题型分布/练习趋势。无记录返回 0 / 诚实空状态，**无模拟数据**。前端 ECharts（Bar/Line）消费这些真实端点。

## 五、向量检索设计（刻意取舍）

- 存储：`biz_document_chunk.embedding` MySQL JSON 数组（1024 维），无独立向量数据库。
- 检索：每次查询实时取「当前用户 + 目标知识库」的切片（SQL 走 `idx_user_kb` 预过滤），JVM 内 `VectorMath` 余弦相似度排序取 Top-K。
- 守卫：VectorMath 处理 null/维度不符/NaN/零范数；非法向量跳过并记录日志。
- **无常驻索引、无向量缓存**： transient 内存占用约几 MB，N=288×D=1024 约 29.5 万次浮点乘加，计算量可忽略；检索延迟主导成本为 Embedding API 往返（实测平均约 437ms）。
- 扩展阈值：切片量约 >1 万需评估 ANN 索引。**替换向量存储架构需用户确认**，M7 未变更。

## 六、异步与稳定性

| 线程池 | core/max/queue | 用途 |
|--------|----------------|------|
| parseExecutor | 2/4/100 | PDF 解析 |
| analysisExecutor | 2/2/50 | AI 论文分析 |
| vectorExecutor | 2/2/50 | 切片+Embedding |
| chatExecutor | 2/4/50 | RAG 对话 |
| planQuizExecutor | 2/2/50 | 计划/出题生成 |

均 AbortPolicy + waitForTasksToCompleteOnShutdown + awaitTermination 30–60s。AI 客户端连接超时 10s、读超时 120s（Embedding 60s），max-concurrent 2。Redis 异常限流 fail-open；锁 TTL 到期恢复卡住任务。

## 七、前端架构

- 状态：Pinia `user`（token/userInfo/login/logout/loadUser）、`app`（theme 持久化/mobileNavOpen）。
- 路由：`beforeEach` 守卫（非 public 无 token→/login；已登录访问认证页→/；动态标题；catch-all→/）。
- 请求：Axios 统一解包 Result，401→清 token+跳登录，429/超时/网络友好提示；上传超时 120s，普通 15s。
- 渲染：marked + DOMPurify 安全渲染 AI Markdown；ECharts 按需 Bar/Line。
- 刷新恢复：token 存 localStorage，MainLayout.onMounted 拉取 /users/me。
- UI：iOS 玻璃拟态、CSS 变量明暗双主题、≤900px 抽屉导航；加载/空/错误/成功状态完整。

## 八、数据隔离与级联清理

- 所有业务查询按 user_id 过滤；跨用户访问 403/404。
- 删除文档：逻辑删除 + 切片物理清理 + 物理 PDF 删除 + 解析 JSON 删除。
- 删除知识库：级联逻辑删除其文档 + 物理文件清理（双重守卫 kbId+userId）。
- 派生数据（切片/向量）随文档删除物理重建，避免脏检索。