# M4 交付报告：文档切片与 Embedding

日期：2026-11-17
范围：原始文档文本 → 清洗切片 → DashScope Embedding → MySQL JSON 向量入库 → 反序列化 → 余弦检索。
**本阶段未开发聊天问答界面，未实现完整 RAG**（M5 预留）。

---

## 1. 真实模型与参数（全部来自真实调用，非 Mock）

| 项 | 值 |
|---|---|
| 服务商 | 阿里云 DashScope（OpenAI 兼容端点 `/compatible-mode/v1/embeddings`） |
| 模型名称 | `text-embedding-v3` |
| 向量维度 | **1024**（请求参数 `dimensions=1024`，落库 `embedding_dimension=1024`） |
| 版本标记 | `v3`（落库 `embedding_version`，检索按 模型+版本+维度 三元组过滤） |
| 编码格式 | `encoding_format=float` |
| 批量上限 | 10 条/批（DashScope 限制，配置 `batch-size=10`，代码钳制 1..10） |
| 超时 | 连接 10s / 读取 60s（`EmbeddingProperties` 集中配置） |
| 并发限制 | 信号量 max-concurrent=2，获取等待 30s |
| API Key | 仅后端环境变量 `DASHSCOPE_API_KEY`；前端、Git、日志均不含密钥；前端不直连 Embedding API |

切片参数集中配置于 `studypilot.chunk`（`ChunkProperties`，不散落业务代码）：
`target-chars=500`、`overlap-chars=80`、`min-chunk-chars=50`、`max-chunks-per-doc=2000`。

## 2. 实现要点

- **切片**（`ChunkService`）：CRLF/控制字符/多余空白清理；按行累积保留段落边界；章节标题（编号节/第X章/摘要等）硬边界且不跨重叠；重叠 80 字符按行尾种子实现；尾块 <50 字符合并；每 Chunk 记录 `doc_id/user_id/kb_id/page_num/source_position`（清洗后全文偏移 `p{页}:{起}-{止}`）；稳定 UID = SHA-256(docId:index:SHA-256(content))，内容不变则 UID 不变（已实测）。
- **存储**（已批准方案，不引入向量数据库）：`biz_document_chunk` 单表存切片+向量，`embedding` 为 MySQL JSON 列（非法 JSON 在 DB 层即被拒绝）；`biz_document` 增加 `vector_status/chunk_count/embedding_count/vector_error_msg`。迁移 `sql/migration-m4.sql` 采用 ALTER 增量方式，未触碰已有数据。
- **任务状态机**（`DocumentVectorService` + `DocumentVectorExecutor`）：PENDING/PROCESSING/SUCCESS/FAILED；Redis SETNX 锁（TTL 15min）+ 状态 CAS 防重复并发；SUCCESS 后重新处理需 `reprocess=true` 确认（否则 400 提示覆盖）；失败物理清理切片/向量并归零计数，支持重试；进度 = embeddingCount/chunkCount 真实落库。
- **检索**（`POST /api/knowledge-bases/{kbId}/search`，kbId=0 为未归属池）：先执行用户所有权/知识库权限过滤；SQL 层按 模型+版本+维度 三元组一致性过滤；Java 层 `VectorMath.cosine` 校验空向量/维度不一致/NaN/Infinity/零范数（防除零），非法向量安全跳过不崩溃；TopK 默认 5、上限 20；查询 ≤2000 字符。
- **级联清理**：删除文档/删除知识库/重新解析成功/重新向量化，均物理删除派生切片与向量（派生数据不做逻辑删除），全库无孤立向量记录（已实测）。
- **前端**（知识库页面）：新增「向量化」列——状态标签（未处理/向量化中/已就绪/向量化失败）、真实 `n/m 向量` 计数与百分比进度、失败原因展示；向量化/重新处理按钮（已完成文档弹覆盖确认框）；PROCESSING 期间 3s 轮询 `vector-status`；所有状态来自后端真实数据。

## 3. 测试结果（全部真实执行）

### 3.1 单元测试 17/17 PASS
`VectorMathTest` 9 项（同向=1/反向=-1/正交=0/已知值/维度不一致/空与null/NaN与Infinity/零范数/1024维稳定性）；`ChunkServiceTest` 8 项（清洗/单块页码位置UID/多块重叠与长度上限/内容变更UID变化/标题边界/跨页追踪/空文本/尾块合并）。

### 3.2 真实全链路 35/35 PASS（`m4-chain-test.mjs`，真实 DashScope 调用）
- paper.pdf（5页 3749字符）→ 解析 SUCCESS → **16 切片** → 2 批 Embedding → SUCCESS 16/16，progress=100。
- DB 核验：行数=chunkCount；`JSON_LENGTH(embedding)` 全部 1024；模型/维度/版本 = text-embedding-v3/1024/v3；user_id/kb_id 正确；UID 唯一且 64 位；页码 1..5、source_position 无空值。
- 重复处理：处理中并发提交 **429**；已完成再提交 **400 覆盖确认**；`reprocess=true` 重建后 **UID 集合完全一致**、无残留旧切片。
- medium.pdf（12页 48474字符）→ **120 切片、12 批**真实调用；处理中进度真实递增 **8%→25%→50%→66%→91%**；`log_ai_call` biz_type=3 成功记录增量=12。
- 真实余弦检索：相关查询 top=0.6832 > 无关查询 top=0.4680（语义相关性成立）；分数降序且 ∈[-1,1]；topK=999 钳制为 20；空白查询 400。
- 用户隔离：lisi 访问 demo 文档状态/提交/检索均 **404**；lisi 自身检索不泄露 demo 向量（0 条）。
- 守卫：扫描版（needOcr）与解析失败文档提交向量化均 400。
- 删除清理：删除文档后切片/向量 **0 行**；删除知识库级联 **0 行**（文档逻辑删除标记正确）；全库孤立向量检查 **0**。

### 3.3 检索过滤夹具测试 5/5 PASS（`m4-filter-test.mjs`）
向 kb1 临时插入 7 条明确标记的夹具行（测试后立即物理删除，不留存假数据）：
不同模型（v2）、不同维度（1536）、不同版本（v2）→ SQL 三元组排除；空向量 `[]`、维度不符 `[0.1,0.2]`、非数组 JSON、1024 维零范数 → Java 层安全跳过。含夹具检索仍 200、结果集与基线完全一致；夹具清理后行数复原（272→279→272）。

### 3.4 Embedding 失败路径 21/21 PASS（`m4-error-test.mjs` + 明确标记的测试桩）
桩仅用于异常映射测试（非真实 Embedding），后端临时指向 `http://127.0.0.1:9099/v1`、读取超时临时调为 3s：

| 模式 | 前端可见错误文案（脱敏） | 失败后清理 |
|---|---|---|
| 429 | Embedding 服务限流，请稍后重试 | 切片 0 行、计数归零、状态 FAILED ✓ |
| 500 | Embedding 服务暂时不可用，请稍后重试 | ✓ |
| 401 | Embedding 服务认证失败，请检查 API Key 配置 | ✓ |
| 慢响应(>3s) | Embedding 服务响应超时，请稍后重试 | ✓ |
| 返回维度 8≠1024 | Embedding 返回维度与配置不一致（期望 1024） | ✓ |
| 返回含 null | Embedding 返回非法数值，请重试 | ✓ |
| 返回数量不符 | Embedding 返回结果异常，请重试 | ✓ |

### 3.5 失败后真实重试恢复 1/1 PASS（`m4-recover-test.mjs`）
切回真实配置后，FAILED 文档（doc29）`reprocess=true` 重试 → 真实 Embedding → SUCCESS 16/16。

### 3.6 浏览器真实操作验证（demo 账号，截图 `test-assets/m4-knowledge-ui.png`）
- 向量化列正确显示：已就绪 16/16、已就绪 120/120、向量化失败+原因、未处理+向量化按钮；加密/解析失败文档无向量化按钮。
- 已完成文档点「重新处理」→ 覆盖确认弹窗；**取消后网络日志无任何 vectorize 请求**；确认后 POST 200 并启动轮询。
- medium 重新处理 DOM 实测进度：`向量化中 0/0 · 0%` → `向量化中 70/120 · 58%` → `已就绪 120/120`。

### 3.7 必测 7 项对照
| # | 要求 | 覆盖 |
|---|---|---|
| ① | 不同维度不能比较 | VectorMathTest + 夹具 P3（1536 维被排除、2 维被跳过） |
| ② | 不同模型不能混用 | 夹具 P3（v2 模型/版本 SQL 排除）+ 检索三元组过滤 |
| ③ | 空向量处理 | VectorMathTest（空/null/零范数）+ 夹具 `[]` 跳过 |
| ④ | 重复处理 | A4 并发 429、A11 覆盖确认 400、A13 UID 稳定重建、E2b 429 |
| ⑤ | Embedding API 失败 | 3.4 七种失败模式 + 3.5 真实重试恢复 |
| ⑥ | 用户数据隔离 | C1 跨用户 404、C2 不泄露、检索权限前置过滤 |
| ⑦ | 删除文档后关联清理 | F2/F4 物理清空、F5 全库无孤立向量 |

## 4. 真实调用统计（log_ai_call，biz_type=3 RAG）

| 状态 | 次数 | prompt tokens | 平均延迟 | 最大延迟 |
|---|---|---|---|---|
| 成功（全部真实 DashScope） | **77** | **45,567** | 342ms | 828ms |
| 限流（桩 429） | 2 | 0 | 18ms | 34ms |
| 失败（桩 500/401/维度/非法值/数量） | 10 | 6 | 11ms | 20ms |
| 超时（桩慢响应） | 2 | 0 | 3003ms | 3004ms |

当前库内真实向量数据：5 篇文档共 **288 个切片**（doc20/24/29 各 16、doc21/25 各 120），维度全部 1024，模型/版本全部 text-embedding-v3/v3。doc28 保留为 FAILED 示例（错误路径测试产物，界面如实展示失败原因）。

关于额度：M4 开始时曾担心 API 无额度；经真实探测与本轮 77 次成功调用证实 **Embedding 配额可用**，费用极低（约 4.6 万 prompt tokens）。

## 5. 本轮发现并修复的真实缺陷

1. **`@Async` 同类自调用失效**（真实缺陷）：`DocumentVectorService.submitVectorize` 直接调用本类 `vectorizeAsync`，绕过 Spring 代理，导致整个向量化阻塞 HTTP 线程（日志证实跑在 `nio-8080-exec`，提交接口同步返回终态、无法观测进度）。修复：按 M2/M3 既有模式拆出独立 Bean `DocumentVectorExecutor` 跨 Bean 调用。修复后日志线程为 `doc-vector-1/2`，提交即返 PROCESSING，重测 35/35 PASS。
2. 测试脚本侧一次 SQL 瞬时空读（非产品缺陷，DB 状态核验正确）：为测试脚本 SELECT 增加一次重试后错误套件 21/21 PASS。

## 6. 已知边界（如实说明）

- 扫描版 PDF（needOcr）无文本可切片，向量化被明确拒绝；OCR 未接入（不在 M4 范围）。
- big.pdf（940,080 字符，约 1900 切片）未做真实向量化：接近 maxChunks=2000 上限、约 190 批调用，耗时与费用不必要；多批次能力已用 medium.pdf（120 切片/12 批）真实验证。
- 余弦检索为服务层全量扫描 O(n)，当前项目规模（数百切片）毫秒级；未引入向量数据库（按已确认方案）。
- M1 遗留 11 张表注释乱码修复仍待批准，未在本轮处理。

## 7. 交付物清单

- 后端：`ChunkProperties` / `EmbeddingProperties` / `VectorStatus` / `VectorMath` / `DocumentChunk` / `DocumentChunkMapper` / `ChunkService` / `DashScopeEmbeddingClient` / `DocumentVectorService` / `DocumentVectorExecutor` / `VectorController` / 相关 DTO·VO；`AsyncConfig` vectorExecutor；文档删除/知识库级联/解析重置钩子。
- 前端：`types/api.ts` 向量类型、`api/vector.ts`、`KnowledgeView.vue` 向量化列+操作+轮询+覆盖确认。
- SQL：`sql/migration-m4.sql`（已执行）、`sql/init.sql` 同步。
- 测试：`VectorMathTest` / `ChunkServiceTest`（17 项）；`test-assets/m4-chain-test.mjs`、`m4-filter-test.mjs`、`m4-error-test.mjs`、`m4-embed-stub.mjs`、`m4-recover-test.mjs`、`GenMediumPdf.java`/`medium.pdf`、截图 `m4-knowledge-ui.png`（测试资产在仓库外）。
- 构建：后端 `mvn test` BUILD SUCCESS（17/17）；前端 `vue-tsc --noEmit` 与 `vite build` 均通过。

## 8. 状态

**M4 已完成，暂停等待确认，未进入 M5。** Git 提交将按既有阶段提交流程在用户指示后执行。