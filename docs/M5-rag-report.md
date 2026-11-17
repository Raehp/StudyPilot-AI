# M5 交付报告：RAG 多轮智能问答

日期：2026-11-17
范围：用户提问 → 权限校验 → 问题 Embedding → 指定知识库向量检索（MySQL JSON + Java 内存余弦）→ Top-K + 阈值过滤 → 上下文 Prompt → 通义千问流式生成 → SSE 逐步返回 → 用户消息与 AI 回答落库 → 后端组装引用溯源。
**沿用 M4 已验收的 MySQL JSON + Java 内存余弦检索方案，未引入向量数据库；未实现 M6 功能。**

---

## 1. 真实模型与参数（全部来自真实调用，非 Mock）

| 项 | 值 |
|---|---|
| 服务商 | 阿里云 DashScope（OpenAI 兼容端点 `/compatible-mode/v1`） |
| 生成模型 | `qwen-plus`（`/chat/completions`，`stream=true` + `stream_options.include_usage=true`） |
| 生成温度 | `temperature=0.3` |
| Embedding 模型 | `text-embedding-v3`，维度 **1024**，版本标记 `v3`（复用 M4） |
| 流式协议 | 服务端 `text/event-stream` 增量 `choices[0].delta.content`，`[DONE]` 收尾，`usage` 统计 token |
| API Key | 仅后端环境变量 `DASHSCOPE_API_KEY`；前端、Git、日志均不含密钥；前端不直连模型 API |

RAG 参数集中配置于 `studypilot.rag`（`RagProperties`，不散落业务代码）：

| 配置 | 值 | 含义 |
|---|---|---|
| `top-k` | 5 | 默认检索条数（请求可覆盖，钳制 1..20） |
| `min-score` | 0.5 | 最低相似度阈值（请求可覆盖，钳制 0..1） |
| `max-question-chars` | 2000 | 单问长度上限 |
| `max-history-messages` | 10 | 上下文历史消息条数上限 |
| `max-history-chars` | 4000 | 上下文历史字符预算（从最新往回保留，至少 1 条） |
| `max-context-chars` | 8000 | 检索内容字符预算（超出截断并标注） |
| `sse-timeout-ms` | 180000 | SseEmitter 超时 |
| `max-generation-ms` | 180000 | 单次生成硬 deadline（防后台任务无限运行） |
| `stream-idle-timeout-ms` | 60000 | 流式相邻 chunk 空闲读超时 |

## 2. 模块拆分（检索 / 生成 / 引用 / 聊天历史分离，非单一巨型 Service）

| 模块 | 职责 |
|---|---|
| `ConversationService` | 会话 CRUD、所有权校验、消息落库、自动命名、**历史上下文加载（条数+字符双预算）**、残留 GENERATING 收敛、引用 JSON 读写 |
| `RagRetrievalService` | 检索编排：钳制 topK/阈值 → 调 `DocumentVectorService.search` → 按最低相似度过滤 |
| `DocumentVectorService`（M4 扩展） | 余弦检索；**新增已删除文档过滤**（候选 chunk 的 docId 必须仍存在于 `biz_document`） |
| `RagPromptBuilder` | 系统提示词（5 条反伪造规则）+ 历史 + 检索内容编号组装 + 字符预算截断 |
| `DashScopeStreamClient` | 流式生成客户端：信号量并发限制、逐 chunk 回调、取消/deadline 检查、错误脱敏映射、`log_ai_call` 记录（biz_type=3） |
| `ChatStreamService` | 编排：请求线程完成校验/检索/落库/组装引用，`chatExecutor` 异步生成，SSE 事件 meta/delta/done/error，终态落库 |
| `ChatStreamRegistry` | 活跃生成注册表：防同会话并行、断开/主动停止取消、历史加载收敛残留 |
| `ChatController` | REST + SSE 端点 |

异步采用显式 `chatExecutor.execute(...)`（线程前缀 `chat-gen-`，日志已核验），避免 `@Async` 自调用失效；线程池 core 2 / max 4 / queue 50 / AbortPolicy / 优雅停机等待 30s。

## 3. 后端接口（JWT 走 Authorization 头）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat/sessions` | 创建会话（绑定 kbId，0=未分配池；校验知识库归属） |
| GET | `/api/chat/sessions` | 会话列表（仅本人，按更新时间倒序，含 kbName） |
| GET | `/api/chat/sessions/{id}/messages` | 消息历史（含引用快照、终态；收敛残留 GENERATING） |
| DELETE | `/api/chat/sessions/{id}` | 删除会话（活跃生成中返回 429；级联逻辑删除消息） |
| POST | `/api/chat/sessions/{id}/messages` | 发送问题，**单 POST 返回 SSE**（meta/delta/done/error） |
| POST | `/api/chat/sessions/{id}/cancel` | **主动停止当前会话生成**（校验归属 → 取消注册表活跃生成 → 落 STOPPED） |

> `cancel` 为本阶段新增端点：浏览器 `AbortController.abort()` 关闭连接的方式在 Windows localhost Tomcat 下不被即时判定为断开（见 §9），仅靠 TCP 断开检测无法保证浏览器「停止」按钮可靠取消服务端生成。新增显式取消接口后，停止按钮端到端落 STOPPED 已实测（§6.4）。被动断开（原始 socket  abrupt close）仍由 emitter 回调取消，API 层已实测 STOPPED。

SSE 事件：`meta`（userMessageId/assistantMessageId/retrievalCount/citations）→ 多个 `delta`（content 增量）→ `done`（完整 ChatMessageVO）；异常时 `error`（脱敏 message，**不含堆栈/内部类名**）。无命中时直接 delta+done 输出明确提示，**不调用 LLM、不伪造引用**。

## 4. 实现要点

- **检索**：仅检索指定知识库；SQL 层按 用户 + 知识库 + 模型+版本+维度三元组 过滤（复用 M4）；Java 内存余弦（`VectorMath.cosine`，防除零/NaN/维度不一致）；Top-K 降序返回相似度分数；最低阈值过滤；**已删除文档的 chunk 不被检索**（M5 新增过滤）。
- **引用溯源**：引用由**后端**从真实检索结果 `VectorSearchResultVO` 组装（documentId/documentName/chunkId/chunkUid/pageNum/originalContent/similarityScore），以 JSON 快照存于 `biz_chat_message.citations`；AI 仅在正文标注 `[n]`，**不得自行生成引用 ID**；无有效来源则无引用。前端点击引用卡片展开原文片段 + 页码 + 相似度百分比 + chunkId；原文档已删除时前端按快照展示并提示（内容接口 404 → 标记 deleted）。
- **多轮上下文**：历史仅取本人本会话 `status=SUCCESS` 消息，`LIMIT maxHistoryMessages`（10）+ 字符预算（4000，从最新往回保留至少 1 条），**不无限加载全部历史**；所有会话绑定 `user_id`。
- **Prompt 反伪造**：系统提示词强制 5 条规则——优先依据检索内容回答；不得把无依据猜测说成事实；知识库缺少的信息明确说明「未找到相关信息」；不得伪造论文标题/作者/页码/引用；多来源冲突明确指出。真实回答已核验严格区分「知识库内容」与「常识性参考（非来自知识库）」（§7）。
- **终态一致性**：`MessageStatus` GENERATING(0)/SUCCESS(1)/FAILED(2)/STOPPED(3)；生成成功→SUCCESS+token；失败→FAILED+脱敏 error_msg；断开/主动停止/deadline→STOPPED+部分内容；历史加载时不在注册表的残留 GENERATING 收敛为 STOPPED。`log_ai_call` status：0 成功 / 1 失败 / 2 超时 / 3 限流。
- **取消与防无限运行**：注册表 `AtomicBoolean cancelled`；emitter onCompletion/onTimeout/onError → cancel；主动停止接口 → cancel；流式循环逐 chunk 检查 cancelled + `maxGenerationMs` deadline；`streamIdleTimeoutMs` 读超时。后台任务必然终止。
- **前端 SSE 认证**：`fetch + ReadableStream`，JWT 放 Authorization 头，**绝不放入 URL 查询参数**；401 → 清 token 跳登录；非 SSE 响应解析 JSON 业务错误抛出。
- **前端聊天界面**：左侧历史会话（创建/选择/删除确认）+ 知识库选择器（含未分配池）；中间 Markdown 渲染（`marked` + `DOMPurify.sanitize`，防 XSS）；流式光标 ▍；停止按钮（abort + cancel 接口）；失败重试；引用卡片展开原文/页码/相似度；token 消耗展示；Enter 发送 / Shift+Enter 换行；停止后轮询历史收敛终态（窗口 15s，超出本地置 STOPPED 避免光标永久卡住）。

## 5. 数据库迁移（已获用户批准 2026-11-17）

`sql/migration-m5.sql` 仅新增列，不改动已有列与数据：

```sql
ALTER TABLE `biz_chat_message`
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '消息状态：0-生成中 1-成功 2-失败 3-已停止' AFTER `token_count`,
  ADD COLUMN `error_msg` varchar(255) NOT NULL DEFAULT '' COMMENT '失败/停止时的脱敏提示信息' AFTER `status`;
```

`sql/init.sql` 同步更新（citations 注释 + status/error_msg 列）。会话/消息表 `biz_conversation`/`biz_chat_message` 沿用 M1 已建结构。

## 6. 测试结果（全部真实执行，最终代码状态）

### 6.1 单元测试 25/25 PASS（`mvn test`，BUILD SUCCESS）
- `RagPromptBuilderTest` 5：反伪造规则齐全 / 检索编号 / 历史角色映射 / 字符预算截断 / 空历史。
- `ConversationHistoryTest` 3：消息条数上限（ArgumentCaptor 核验 SQL `LIMIT 10`）/ 字符预算从最新保留 / 单条超长仍保留。
- `VectorMathTest` 9 + `ChunkServiceTest` 8（M4 回归）。

### 6.2 主链路真实测试 42/42 PASS（`m5-chat-test.mjs`，真实 DashScope 调用，DB 直查断言）
A 会话 CRUD/所有权（6）；B 正常检索+SSE+引用一致性（8）；C 多轮（2）；D 无命中提示（3）；E 用户/知识库隔离（5）；F 断开→STOPPED（4）；G 已删除文档引用（9）；H 模型/版本/维度过滤（2）；I 删除级联（3）。

### 6.3 故障路径 16/16 PASS（`m5-error-test.mjs` + 明确标记的测试桩 :9098，后端临时 :8081）
桩仅用于异常映射（非真实生成，`log_ai_call` 已标注）：ok 基线 / 429 限流（status=3）/ 500 / 401 认证失败 / 空闲超时（3-8s 终止，status=2）/ 空返回 / 硬 deadline（slowforever，STOPPED+部分内容，6-12s 终止）/ 错误脱敏（无堆栈/类名泄露）/ 失败后即时重试 / 无残留 GENERATING。

### 6.4 浏览器真实操作验证（demo 账号，localhost:5173，零控制台错误）
- 创建会话 + 选择知识库（深度学习论文库 v2，13 篇）→ 提问 → **SSE 流式显示 + 光标 ▍** → Markdown 渲染（标题/加粗/列表）。
- 引用卡片 5 张：文档名/页码/相似度百分比；**展开原文与 DB 逐字一致**（chunkId=93 ↔ `biz_document_chunk` id=93，第7页 Related Work，doc 21/kb 1）。
- 自动命名（首问截断）、token 消耗展示、多轮消息持久化。
- 无命中明确提示（泛化追问低于阈值 0.5），**无伪造引用**。
- **停止按钮端到端**：长回答生成早期（3s）点击停止 → `cancel` 接口 → DB 消息 128 = **STOPPED**（174 字符部分内容，error_msg「客户端断开或用户停止」）→ UI 显示「已停止生成，以上为部分内容」，光标消失，composer 恢复。
- 停止后**立即再次发送成功**（注册表已清理，无 429）。
- 前端 `typecheck`（vue-tsc）+ `build` 通过（ChatView 94.56 kB，含 marked+DOMPurify）。

## 7. 真实问答演示记录（引用核对来自原文）

**Q1：这篇论文的主要研究内容是什么？**
A1（节选）：研究内容聚焦于**联邦学习中的隐私保护聚合与通信效率问题** `[1–5]`；所有来源重复出现一致表述 “federated learning privacy preserving aggregation and communication efficiency study”；明确标注「当前检索内容仅提供高度重复短语，未说明具体方法/实验/标题/作者，无法判断是否提出新算法」。
引用（后端组装，DB 核验一致）：

| # | 文档 | chunkId | 页码 | 相似度 |
|---|---|---|---|---|
| 1 | medium | 93 | 7 | 0.5426 |
| 2 | medium | 787 | 7 | 0.5426 |
| 3 | medium | 43 | 2 | 0.5420 |
| 4 | medium | 737 | 2 | 0.5420 |
| 5 | medium | 33 | 1 | 0.5400 |

原文核对：chunk 93 内容 = “2 Related Work (page 7) / Page 7 line 1..4: federated learning privacy preserving aggregation and communication efficiency study.”，与引用卡片 `originalContent` 逐字一致（浏览器展开 + DB 双向核验）。

**Q2：它使用了什么研究方法？**
A2（节选）：知识库**未找到具体研究方法描述**；仅能确认实验设置（CIFAR-10，Dirichlet α=0.5 non-IID 划分 `[1–3]`）与性能对比结果（准确率 94.2%、相比 FedAvg 通信效率提升 87.3%、收敛加快 1.6× `[4–5]`），并明确「这些是评估结果而非方法本身，具体技术路径完全未说明」。
→ 多轮上下文生效（承接 Q1），且严格区分「检索到的事实」与「缺失信息」，未伪造方法/标题/作者。

## 8. 真实调用统计（`log_ai_call`，biz_type=3 RAG）

| 类别 | 次数 | 平均耗时 | 最大耗时 | prompt tokens | completion tokens |
|---|---|---|---|---|---|
| **RAG 真实生成**（qwen-plus，latency>1000ms，status=0） | 22 | 7283ms | 13463ms | 12327 | 5730 |
| RAG 桩成功（异常测试基线，明确标注非真实，latency≤1000ms） | 185 | 293ms | — | — | — |
| RAG 桩失败/超时/限流（status 1/2/3） | 17 | — | — | — | — |
| **Embedding**（text-embedding-v3，真实） | 186 | 307ms | 3004ms | 50548 | — |

异步线程 `chat-gen-1/2` 已在后端日志核验；真实生成首 delta 实测可达 ~430ms（流式可行性探针）。

## 9. 本阶段发现并修复的缺陷

1. **401 认证失败被误映射为「网络异常」（真实产品缺陷，已修复）**：JDK `HttpURLConnection` 在请求体流式发送时遇 401 抛 `HttpRetryException`（"cannot retry due to server authentication, in streaming mode"），而非标准 HTTP 错误。`DashScopeStreamClient` 捕获 `RestClientException` 后**遍历 cause 链**识别 `HttpRetryException.responseCode()` 与 `SocketTimeoutException`，正确映射 401/403→认证失败、429→限流、5xx→暂时不可用、超时→响应超时。修复后故障路径 16/16（401 用例由 FAIL 转 PASS）。
2. **浏览器「停止」无法可靠取消服务端生成（已修复）**：浏览器 `AbortController.abort()` 的连接关闭方式在 Windows localhost Tomcat 下不被即时检测，生成跑完落 SUCCESS（停止按钮看似失效）。新增 `POST /api/chat/sessions/{id}/cancel` 显式取消端点 + 前端停止按钮调用，保证落 STOPPED（§6.4 实测）。被动 abrupt disconnect 仍由 emitter 回调取消（API 层 F2 实测 STOPPED）。
3. **前端停止后 UI 可能永久卡在 GENERATING（已修复）**：原 reconcile 轮询窗口 10s 短于长生成（实测 1549 tokens 生成 >10s），窗口耗尽后重载仍 GENERATING 的消息导致光标永久卡住。修复：窗口扩至 15s，耗尽后本地置 STOPPED（保留部分内容），DB 由后端 deadline 保证终态一致。

## 10. 已知边界

- 向量检索为 MySQL JSON + Java 内存余弦（已批准方案），适用于当前数据规模；超大库需后续评估专用向量索引（不在本阶段）。
- 浏览器停止依赖显式 `cancel` 接口保证服务端取消；纯被动断开（如关页）在 localhost 下的检测存在容器差异，但生成受 `maxGenerationMs` deadline 约束必然终止，DB 终态一致。
- 测试桩（:9098/:8081）仅用于异常映射，所有桩调用在 `log_ai_call` 明确标注，**不以桩冒充真实 AI 分析**；真实问答演示（§7）均为真实 qwen-plus 生成。
- 检索质量受限于知识库实际内容（演示库部分文档为高度重复短语），模型按规范明确标注信息缺失，不臆造。

## 11. 安全与交付合规

- API Key 仅后端环境变量；前端/Git/日志均无密钥；前端不直连模型 API；JWT 不入 URL。
- 错误响应脱敏（无堆栈/内部类名，故障路径 8a 实测）；Markdown 经 DOMPurify 消毒防 XSS。
- 迁移仅新增列、未覆盖现有数据；未擅自修改已验收认证模块；未实现 M6 功能。
- 所有测试真实执行，DB 直查断言；引用与原文逐字核对。

## 12. 暂停声明

**M5 已完成并自测通过（单元 25/25 + 主链路 42/42 + 故障路径 16/16 + 浏览器 E2E 零错误）。现暂停，等待用户验收确认，不进入 M6。**

待用户处理事项：
- 轮换曾在聊天中暴露的 DashScope API Key（M3 起遗留）。
- M1 遗留 11 表注释乱码修复仍待批准（非 M5 范围）。
- M5 阶段提交将在用户验收后按既定 git 流程进行（尚未提交）。