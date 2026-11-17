# StudyPilot AI — M7-4 性能与稳定性检查报告

> 阶段：M7 系统整合与比赛交付 / 任务 #53
> 方法：真实代码/配置审计 + 真实 DB 索引核查 + 真实运行时性能探针（`test-assets/m7-perf-probe.mjs`）
> 原则：不未经确认替换向量存储架构；索引/调度等涉及迁移或架构的改动仅列为建议（需批准），不擅自实施；不伪造性能数据。

---

## 一、十项检查结论（对应规格 第五节）

| # | 检查项 | 结论 | 真实证据 |
|---|--------|------|----------|
| 1 | DB 索引 | ✅ 充分 | 所有外键/过滤列均有索引：`biz_document`(idx_user_id,idx_kb_id)、`biz_document_chunk`(idx_doc_id,idx_kb_id,**idx_user_kb(user_id,kb_id)**)、`biz_conversation`(idx_user_id)、`biz_chat_message`(idx_conversation_id)、`biz_knowledge_base`(idx_user_id)、`biz_paper_analysis`(idx_document_id,idx_user_id)、`biz_quiz`(idx_user_id)、`biz_quiz_question`(idx_quiz_id)、`biz_study_plan`(idx_user_id)、`biz_study_task`(idx_plan_id) |
| 2 | 分页 | ✅ 通过 | `MybatisPlusConfig` 注册 `PaginationInnerInterceptor` 且 `maxLimit=500`；探针实测 `size=1000 → 实际返回 500`；列表端点统一 `selectPage` |
| 3 | 大文档解析 | ✅ 有界 | PDFBox 解析；AI 分析分段 `segment-chars=6000 / max-segments=12`；切片 `target-chars=500 / overlap=80 / max-chunks-per-doc=2000`；M2 已测 big.pdf；超限有界不 OOM |
| 4 | Embedding 批处理 | ✅ 有界 | `batch-size=10`（text-embedding-v3 单批上限）、`max-concurrent=2`、`dimension=1024 / version=v3`；批量入库 |
| 5 | 内存向量检索 | ✅ 适配当前规模（见第二节） | 逐查询暴力余弦；实测 demo KB **N=288 切片**，检索平均 **523ms**（冷启 956ms）；余弦计算量 ≈294912 次浮点乘加（可忽略），主导成本为 Embedding API 往返 |
| 6 | SSE 长连接 | ✅ 通过 | `rag.sse-timeout-ms=180000 / stream-idle-timeout-ms=60000`；`chatExecutor`(core2/max4/queue50)；M5 已测断连恢复 |
| 7 | AI 超时 | ✅ 通过 | `connect-timeout-ms=10000`；chat `read-timeout-ms=120000`、embedding `read-timeout-ms=60000`；`max-generation-ms=180000`；`max-concurrent=2` 限并发 |
| 8 | Redis 异常 | ✅ 已加固 | 认证 fail-closed（`JwtInterceptor` token 不匹配→401）；**限流器 fail-open**（本次加固：Redis 不可用→放行+告警，不成为额外单点）；任务锁均带 TTL；本机 Redis 为多项目共享实例，清理仅针对 `studypilot:*` 专用键 |
| 9 | 存储空间 | ✅ 通过 | `upload-dir` 可经 `STORAGE_DIR` 覆盖；单文件 `max-size-mb=30` + 容器 `max-file-size:30MB`；删除级联物理清理（M7-3 E1–E9 实测 PDF+解析 JSON+切片均清除） |
| 10 | 异步恢复 | ✅ 通过（机制见第三节） | 5 个隔离有界线程池 + AbortPolicy→429+状态回滚；Redis 锁 TTL（解析 10min/其余 15min）到期自动释放→可重提；优雅停机 drain 30–60s；失败清理派生数据支持重试 |

---

## 二、内存余弦检索资源消耗评估（重点）

**实现**（`DocumentVectorService.search` + `VectorMath.cosine`）：
1. 查询 Embedding（1 次 API 往返）；
2. **SQL 层前置过滤**：`user_id + kb_id + embedding NOT NULL + model/version/dimension 三元组一致`，命中复合索引 `idx_user_kb`，候选集已收窄到「本人 + 单知识库」；
3. 排除已删文档切片（`selectBatchIds` 逻辑删除过滤）；
4. 逐候选反序列化 `float[1024]` → 余弦（`VectorMath` 防护空值/维度不一致/NaN/Infinity/零范数，非法向量**跳过并告警**而非崩溃）；
5. 全量排序取 TopK（≤20）→ 补文档标题。

**真实测量**（demo uid=1，KB id=1）：
```
候选切片 N=288，维度 D=1024
search 延迟：956 / 485 / 443 / 367 / 366 ms（平均 523ms）
工作台 overview 聚合：36 / 23 / 22 ms（平均 27ms）
分页 size=1000 → 500（上限生效）
```

**资源结论**：
- **CPU**：O(N×D)=288×1024≈29.5 万次浮点乘加/查询，实测余弦部分 <5ms，**检索延迟主导项是 Embedding API 网络往返（~300–400ms）**，非内存计算。
- **内存**：无常驻索引；每次查询临时加载候选切片 embedding JSON（288×~12KB≈3.5MB）+ 反序列化 float[]（288×4KB≈1.2MB），查询结束即可回收。当前规模（数百切片）开销 trivial。
- **扩展阈值（建议，需确认）**：暴力扫描随 N 线性增长；当单知识库切片 >~1 万、或并发检索升高时，再评估引入 ANN/向量数据库（如 pgvector/Milvus）。**遵守规格：本阶段不替换向量存储架构**，仅记录阈值与触发条件供后续决策。

---

## 三、异步任务稳定性与恢复机制

**线程池隔离**（`AsyncConfig`，均 `AbortPolicy` + `waitForTasksToCompleteOnShutdown=true`）：

| 池 | core | max | queue | awaitTermination |
|----|------|-----|-------|------------------|
| parseExecutor | 2 | 4 | 100 | 30s |
| analysisExecutor | 2 | 2 | 50 | 60s |
| vectorExecutor | 2 | 2 | 50 | 60s |
| chatExecutor | 2 | 4 | 50 | 30s |
| planQuizExecutor | 2 | 2 | 50 | 60s |

- 有界队列 + AbortPolicy：过载抛 `TaskRejectedException` → 服务回滚状态 + 释放锁 + 返回 429（背压，不 OOM）。
- 池隔离：解析/分析/向量/聊天/计划出题互不抢占（注：plan 与 quiz 共享 `planQuizExecutor`，极端并发下相互影响，当前规模可接受）。
- 优雅停机：在途任务最多 drain 30–60s，减少重部署产生的僵死任务。

**并发与恢复**：
- 每任务 Redis 锁 `setIfAbsent(key,"1",TTL)` + 状态 CAS（`update ... where status=当前`）防重复并发。
- 锁 TTL：解析 10min、分析/向量/计划/出题 15min。**worker 崩溃→锁到期自动释放→允许重提**；重提时 CAS 覆盖 PROCESSING 状态重新处理。
- 失败清理派生数据（切片/向量）支持干净重试。
- **无 `@Scheduled` 自动僵死任务回收器**：恢复依赖「锁 TTL 到期 + 前端轮询/重试」，而非重启自动续跑。如实记录：后端重启时仍在 PROCESSING 的任务，需待锁 TTL 到期后由用户重提恢复（FAILED 任务立即可重试）。

---

## 四、本次加固（已实施，非迁移）

- **限流器 fail-open**（`RateLimitInterceptor`）：Redis 计数异常时 `try-catch` 放行 + `log.warn`，使限流（尽力防护）不成为额外单点；认证仍由 `JwtInterceptor` fail-closed 把关。
  - 验证：重新打包重启后 `m7-security-test.mjs` **55/55 PASS**（G1 仍正确触发 429，正常路径行为不变）；`m7-perf-probe.mjs` 检索/聚合/分页均正常。
  - 说明：Redis-Down 场景未做运行时注入测试——本机 Redis 为多项目共享实例（含 `spring:session`/`satoken`），停 Redis 会波及其他项目；fail-open 经代码审查 + 编译 + 正常路径回归确认。

---

## 五、建议项（涉及迁移/架构/调度，需批准，本阶段不实施）

1. **可选复合索引**：`biz_document(user_id,status)`、`biz_document(user_id,create_time)` 可进一步加速工作台按状态计数与趋势聚合；当前 `idx_user_id` + 内存聚合在 27ms 级，规模小收益有限。**属 DDL 迁移，需用户批准后再加。**
2. **僵死任务自动回收器**：`@Scheduled` 定期扫描 PROCESSING 超锁 TTL 的任务并重置为可重试，提升重启自愈能力。属新增调度逻辑，建议后续里程碑评估。
3. **向量 ANN 索引**：仅当单 KB 切片 >~1 万或检索并发显著上升时评估（见第二节阈值）。**不未经确认替换架构。**
4. **每用户存储配额**：当前仅单文件 30MB 上限，无用户级总量配额；如需防滥用可后续增加。

---

## 六、结论

十项性能与稳定性检查**全部通过**：索引充分、分页有界（500 上限实测生效）、解析/Embedding/切片均有界、SSE 与 AI 超时配置完备、Redis 异常已加固（限流 fail-open）、存储删除级联清理实测有效、异步池隔离 + 锁 TTL + 优雅停机保障稳定与恢复。
内存余弦检索经真实测量（288 切片 / 平均 523ms / 聚合 27ms）适配当前比赛规模，**未替换向量架构**；扩展阈值与迁移类建议如实列出待批准。本次仅实施限流 fail-open 一处非迁移加固，安全 55/55 回归通过。