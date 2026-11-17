# M7-0 开发前检查与功能清单

> 生成时间：2026-11-18 ｜ 阶段：M7 系统整合与比赛交付 ｜ 检查方式：真实代码 + 真实数据库 + 真实文件，无任何虚构。
> 原则：**不得把未完成的功能标记为完成**。下列分类均以当前仓库实际状态与已执行测试为依据。

## 1. 检查范围与方法（实际执行）

| 检查项 | 方法 | 结果来源 |
|---|---|---|
| 后端所有模块 | `find src/main/java -name *.java` + 控制器 `@*Mapping` 全量 grep | 11 控制器 / ~45 端点 / 38 服务类 / 12 实体 / 12 Mapper |
| 前端所有页面 | `find src -name *.vue/*.ts` + 路由表 | 9 视图 + 8 API 模块 + 2 store + 5 通用组件 |
| 数据库实际结构 | `information_schema.columns` + `SHOW TABLES` + 精确 `COUNT(*)` | 12 表（含逻辑删除过滤） |
| 所有 API 接口 | 控制器注解 grep（含裸 `@GetMapping/@PostMapping`） | 见 §2 |
| AI 服务配置 | `application.yml` + `log_ai_call` 真实聚合 | qwen-plus + text-embedding-v3 |
| 文件存储目录 | `ls data/uploads/{pdf,parsed}` | 真实文件落盘 |
| Redis 使用情况 | grep `RedisTemplate/opsForValue/lock` | 认证态 + 5 类分布式锁 |
| 已完成测试报告 | `ls docs/` + `src/test` | M2–M6 报告 + 6 单测文件 |

## 2. 后端模块与 API 清单（真实）

**控制器（11）**：Auth / User / Health / KnowledgeBase / Document / Paper / Vector / Chat / StudyPlan / StudyTask / Quiz。**无 DashboardController**（工作台统计接口尚未实现）。

| 模块 | 端点 |
|---|---|
| 认证 `/api/auth` | POST register / login / logout |
| 用户 `/api/users` | GET me（**无资料修改/改密端点**） |
| 健康 `/api` | GET health（+ actuator health/info） |
| 知识库 `/api/knowledge-bases` | POST 创建 / GET 列表 / GET·PUT·DELETE {id} / POST {id}/documents / DELETE {id}/documents/{documentId} |
| 文档 `/api/documents` | POST upload / GET 分页 / GET·DELETE {id} / GET {id}/parse-status / POST {id}/parse / GET {id}/content（**无原始 PDF 下载端点**） |
| 论文 `/api/papers` | POST {id}/analyze / GET {id}/analysis / GET {id}/analysis-status |
| 向量 `/api/documents`·`/api/knowledge-bases` | POST {id}/vectorize / GET {id}/vector-status / POST {kbId}/search |
| 问答 `/api/chat/sessions` | POST 创建 / GET 列表 / GET {id}/messages / DELETE {id} / POST {id}/messages(SSE) / POST {id}/cancel |
| 学习计划 `/api/study-plans` | POST 创建 / GET 列表 / GET·PUT·DELETE {id} / POST {id}/generate / GET {id}/generate-status / GET {id}/tasks / GET {id}/progress |
| 学习任务 `/api/study-tasks` | PUT {id}/status / PUT {id} |
| 智能出题 `/api/quizzes` | POST generate / GET 列表 / GET wrong-answers / GET {id} / GET {id}/generate-status / POST {id}/submit / GET {id}/result / DELETE {id} |

**关键服务拆分**（无巨型 Service）：DocumentService/StorageService/PdfParseService(+Task)/PaperAnalysisService(+Task)/ChunkService/DocumentVectorService(+Executor)/DashScopeClient·EmbeddingClient·StreamClient/ConversationService/RagRetrievalService/RagPromptBuilder/ChatStreamService(+Registry)/StudyPlanService·StudyTaskService·StudyPlanGenerator·PlanPromptBuilder·PlanOutputValidator/QuizService·QuizStoreService·QuizGenerator·QuizScoringService·QuizPromptBuilder·QuizOutputValidator/AiOutputParser。

**安全/通用**：JwtInterceptor + JwtUtil + UserContext(ThreadLocal) + AuthConstants；Result/ResultCode/GlobalExceptionHandler/BusinessException/PageResult；VectorMath（余弦，空/维度/NaN/零范数守卫）；状态枚举 DocumentStatus/VectorStatus/AnalysisStatus/GenerateStatus/MessageStatus。

## 3. 前端页面清单（真实）

**视图（9）**：Login / Register / Dashboard / Knowledge / PaperReading / Chat / Plan / Quiz / Settings。
**API 模块（8）**：auth / knowledge / document / paper / vector / chat / plan / quiz。**无 dashboard.ts**。
**Store（2）**：user（JWT/userInfo）/ app（theme）。**通用组件（5）**：StatCard / ChartCard / EmptyState / PageHeader / ModuleEmpty。
**路由**：createWebHistory + beforeEach 守卫（无 token→/login；有 token 访问 login/register→/）；通配重定向 /。
**依赖**：axios / element-plus / @element-plus/icons-vue / pinia / vue-router / echarts / marked / **dompurify**（XSS 净化）。devDeps：vite / typescript / vue-tsc / @vitejs/plugin-vue。**无 vitest**（前端无自动化单测，靠 vue-tsc + build + 浏览器 E2E）。
**脚本**：dev / build / typecheck(vue-tsc --noEmit) / preview。

## 4. 数据库实际结构与真实数据量

12 表（`deleted=0` 为有效数据；总数含逻辑删除）：

| 表 | 总行数 | 有效(deleted=0) | 说明 |
|---|---|---|---|
| sys_user | 15 | 15 | 用户 |
| biz_knowledge_base | 18 | 7 | 知识库 |
| biz_document | 33 | 15 | 文档（status/vector_status 双状态） |
| biz_document_chunk | 288 | 288 | 切片 + JSON 向量(1024维) |
| biz_paper_analysis | 16 | 16 | 论文分析任务历史 |
| biz_conversation | 23 | 2 | 问答会话 |
| biz_chat_message | 166 | 20 | 问答消息（含 citations JSON） |
| biz_study_plan | 9 | 9 | 学习计划 |
| biz_study_task | 112 | 98 | 学习任务（status=1 完成） |
| biz_quiz | 11 | 11 | 测验（status=1 已提交/练习） |
| biz_quiz_question | 25 | 25 | 题目（4 题型 + 判分 + 来源） |
| log_ai_call | 291 | 291 | AI 调用日志（无密钥） |

**状态语义（真实枚举）**：DocumentStatus UPLOADED0/PARSING1/CHUNKING2/EMBEDDING3/READY4/FAILED5；VectorStatus PENDING0/…/SUCCESS2/FAILED3；AnalysisStatus PENDING0/…/SUCCESS2/FAILED3；GenerateStatus NONE0/GENERATING1/SUCCESS2/FAILED3；MessageStatus GENERATING0/SUCCESS1/FAILED2/STOPPED3；StudyTask.status 1=完成；Quiz.status 1=已提交。

**真实状态分布（deleted=0）**：文档 status4=13（其中 vector_status2=5 已向量化、0=7 未向量化、3=1 失败）、status5=2 解析失败；任务 status0=97/status1=1；测验 已提交4 / 未提交7（6 生成失败 + 1 生成成功未练习）。

## 5. AI 服务配置与真实调用

- 生成：DashScope OpenAI 兼容端点 `qwen-plus`（RestClient 封装，非 Spring AI；Boot 3.3.5 锁定）。chatJson 非流式 `response_format=json_object` temp0.2；RAG 流式 SSE。
- 向量：`text-embedding-v3`，1024 维，version v3，batch≤10。
- 密钥：`DASHSCOPE_API_KEY` 仅环境变量（真实 key 在仓库外 `E:/AI-Qoder-Pro/.secrets/dashscope.key`，**待轮换**）。connect 10s / read 120s / max-concurrent 2。
- biz_type：1 摘要(未单独使用) / 2 分析 / 3 RAG / 4 计划 / 5 出题 / 6 判分。
- **log_ai_call 真实聚合（291 条）**：分析 biz2 成功17/失败4/超时1/限流2；RAG biz3 成功219/失败19/超时6/限流6；计划 biz4 成功8；出题 biz5 成功5；判分 biz6 成功4。（失败/超时/限流含 M3/M5 标注的非真实 stub 错误路径测试，报告引用时需区分。）

## 6. 文件存储

`studypilot.storage.upload-dir=${STORAGE_DIR:./data/uploads}`，max-size-mb 30（multipart max-file 30MB / max-request 35MB）。真实落盘：`data/uploads/pdf/`（原始 PDF）+ `data/uploads/parsed/{docId}.json`（解析文本，无 DB 列）。`data/` 已 gitignore。

## 7. Redis 使用

- 认证态：`studypilot:auth:token:{userId}`（JwtInterceptor 校验缓存 token，**单会话语义**：新登录使旧 token 失效）。
- 分布式锁（setIfAbsent + TTL，防重复异步任务）：`studypilot:parse:lock:` / `studypilot:analysis:lock:` / `studypilot:vector:lock:` / `studypilot:plan:lock:` / `studypilot:quiz:lock:`。
- 连接：localhost:6379 无密码（可 env 覆盖），lettuce 池 max-active 8。**当前无基于 Redis 的接口频率限制**。

## 8. 已完成测试报告与单测

- 报告：docs/M2-knowledge-document-report.md（15/15）、M3-ai-paper-analysis-report.md（7/7+8/8+4/4）、M4-embedding-report.md（unit17+real35+filter5+stub21+recovery1）、M5-rag-report.md（unit25+main42+error16+浏览器E2E）、M6-study-quiz-report.md（unit46+集成29PASS/0FAIL/1SKIP+浏览器E2E）。
- 后端单测文件（6，src/test）：VectorMathTest / ChunkServiceTest / RagPromptBuilderTest / ConversationHistoryTest / QuizOutputValidatorTest / PlanOutputValidatorTest。**无 @SpringBootTest 上下文/集成单测**；集成测试由仓库外 `test-assets/m*.mjs` 真实脚本执行。
- 集成脚本：test-assets/m2-final-test.mjs、m3-*.mjs、m4-*.mjs、m5-chat-test.mjs、m5-error-test.mjs、m6-test.mjs。

## 9. 功能清单（四分类）

### A. 已实现并测试通过
1. 认证：注册/登录/登出、JWT+Redis 单会话、BCrypt、统一 Result/全局异常、健康检查（M1 浏览器联调通过）。
2. 用户资料读取 GET /users/me（真实数据）。
3. 知识库 CRUD + 所有权隔离 + 级联删除（M2 15/15）。
4. PDF 上传三重校验 + PDFBox 异步解析 + need_ocr 诚实标记 + 解析内容预览（M2）。
5. AI 论文分析：qwen-plus 12 字段抽取 + 反伪造 + 分段 map-reduce + 任务历史 + Redis 锁/CAS（M3 真实调用 7/7+8/8+4/4）。
6. 文本切片 + Embedding 入库 + MySQL JSON 向量 + 余弦 TopK 检索 + 模型/版本/维度三重过滤 + 级联清理（M4 unit17+real35+filter5+stub21+recovery1）。
7. RAG 多轮问答：SSE 流式 + 引用溯源 + 会话 CRUD + 上下文预算 + 取消 + 消息状态机（M5 unit25+main42+error16+浏览器E2E）。
8. 学习计划：异步生成 + 任务打卡 + 进度重算 + 引用真实性校验（M6 unit+集成+浏览器）。
9. 智能出题：异步生成 + 4 题型 + 客观严格判分 + 简答 AI 判分 + 错题回顾 + 练习/结果模式（M6 unit46+集成29+浏览器）。
10. 前端 UI v2：9 页面 iOS 玻璃拟态浅色/深色主题 + 路由守卫 + axios 拦截器 + 诚实空状态（vue-tsc + build + 浏览器 E2E 零控制台错误）。
11. AI 调用日志 log_ai_call（291 条真实记录，不含密钥）。

### B. 已实现但未完整测试
1. 主题切换（Settings，localStorage 持久化）：仅人工验证，无自动化覆盖。
2. 并发/异常恢复路径：M3/M4/M5/M6 错误路径多为**标注的非真实 stub** 测试；真实大规模并发/压测未做（→ M7-4 性能与稳定性）。
3. 安全防护细项（路径穿越/XSS 净化/Prompt 注入/敏感日志/频率限制）：部分有实现（dompurify、参数化 SQL、env 密钥、反伪造规则），但**未系统性安全测试**（→ M7-3 安全检查）。
4. SSE 长连接稳定性 / AI 超时 / Redis 连接异常 / 异步任务恢复：有单次验证，缺持续/边界压测（→ M7-4）。

### C. 部分实现
1. **个人设置 Settings**：主题切换 ✓ + 资料只读展示 ✓；**资料修改 / 修改密码 / 登录设备管理** 页面诚实标注"后续开放"，后端无对应端点 → 部分实现（非 M7 必需，且改密涉及已验收认证模块，不擅自新增）。
2. **工作台 Dashboard**：页面外壳 + 模块导航 ✓；**9 项真实统计 + ECharts 真实数据未接入**（StatCard `:value=null`、ChartCard `empty`，诚实空状态，无虚构数据）→ 核心统计功能尚未实现（见 D1）。

### D. 尚未实现（M7 待办）
1. **工作台真实统计**（section 二）：无 DashboardController/Service、无 api/dashboard.ts、DashboardView 未接真实数据。9 项统计均可由现有表真实派生（**无需 DB 迁移**，纯新增只读查询）。
2. **Docker Compose 部署**（section 八）：无 Dockerfile / docker-compose / nginx 配置（Glob 确认 0 文件）。
3. **比赛交付文档 ×8**（section 九）：docs/ 仅有 M2–M6 报告；缺 SYSTEM-ARCHITECTURE / DATABASE-DESIGN / API-DOCUMENTATION / DEPLOYMENT-GUIDE / TEST-REPORT / DEMO-GUIDE / KNOWN-ISSUES（README 根目录已有，需 M7 更新）。
4. **比赛演示流程**（section 十）：未编写。
5. **最终验收报告**（section 十一）：未编写。
6. **接口频率限制**（section 四检查项）：当前无 rate limiting。
7. 前端自动化单元测试（vitest）：无（section 七前端测试以 vue-tsc + build + 浏览器 E2E 为准）。
8. 原始 PDF 下载端点 / 资料修改 / 改密 / 设备管理：无（非 M2–M6 必需，诚实标注后续）。

## 10. 工作台真实统计基线（demo uid=1，deleted=0，用于 M7-1 验收对照）

| # | 统计项 | 真实值 | 派生口径 |
|---|---|---|---|
| 1 | 用户文档总数 | 15 | biz_document user_id=1 |
| 2 | 知识库总数 | 2 | biz_knowledge_base user_id=1 |
| 3 | 已解析文档数 | 13 | biz_document status=4(READY) |
| 4 | 已向量化文档数 | 5 | biz_document vector_status=2(SUCCESS) |
| 5 | 聊天会话数量 | 2 | biz_conversation user_id=1 |
| 6 | 学习计划数量 | 4 | biz_study_plan user_id=1 |
| 7 | 已完成学习任务数量 | 1 | biz_study_task status=1（总 42） |
| 8 | 练习次数 | 4 | biz_quiz status=1(已提交) |
| 9 | 学习进度统计 | 平均 1.8% | AVG(biz_study_plan.progress)（或 完成任务 1/42≈2.4%） |

> 以上为**真实数据库当前值**，M7-1 实现后工作台必须展示这些数字（无记录显示 0 / 诚实空状态），不得虚构。

## 11. M7 待办映射

| section | 任务 | 是否需 DB 迁移 | 是否需用户批准 |
|---|---|---|---|
| 二 工作台统计 | #50 | 否（只读新增查询） | 否 |
| 三 全项目联调 | #51 | 否 | 否 |
| 四 安全检查 | #52 | 视发现而定 | **先报告再修复**；如需迁移/改认证模块先暂停 |
| 五 性能稳定性 | #53 | 否 | 不擅自替换向量存储架构 |
| 六 前端优化 | #54 | 否 | 保留 iOS UI，不大规模重做 |
| 七 自动化测试 | #55 | 否 | 未执行不得写通过 |
| 八 部署 | #56 | 否 | 密钥仅 env，不提交 Git |
| 九 交付文档 | #57 | 否 | 与代码一致 |
| 十 演示流程 | #58 | 否 | 基于真实运行系统 |
| 十一 最终验收 | #59 | 否 | **完成后暂停等待最终验收** |