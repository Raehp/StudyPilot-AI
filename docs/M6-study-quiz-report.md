# M6 交付报告：AI 学习计划与智能出题

日期：2026-11-17
范围：学习目标 CRUD → 通义千问异步生成学习阶段/每日任务（可结合指定知识库真实文档）→ 任务完成状态与进度管理（MySQL 持久化）；出题配置 → 基于知识库真实内容异步生成题目（结构化校验 + 引用真实性校验）→ 在线练习 → 客观题自动判分 + 简答题 AI 辅助评分（含评分依据）→ 练习记录持久化 → 历史成绩与错题回顾。
**沿用 M4/M5 已验收的 MySQL JSON + Java 内存余弦检索与 DashScope 封装；未引入新表（仅 4 表 ALTER 增列）；未实现 M7 功能。**

> 状态说明：M6 全部代码已实现并通过编译、前端类型检查与生产构建、46 项单元测试；非 AI 真实集成测试 10 项全部通过。**真实 AI 生成/判分的 9 项集成测试与浏览器端到端验证，因主后端（8080）仍运行 M5 代码、真实 `DASHSCOPE_API_KEY` 仅存于该进程环境而无法在当前会话恢复，暂处于「待密钥」状态——绝不以固定题目或模拟成绩冒充真实 AI。** 待主后端以 M6 代码 + 密钥重启后补全（见 §7）。

---

## 1. 真实模型与参数（非 Mock）

| 项 | 值 |
|---|---|
| 服务商 | 阿里云 DashScope（OpenAI 兼容端点 `/compatible-mode/v1`） |
| 生成模型 | `qwen-plus`（`/chat/completions`，`response_format=json_object`，`temperature=0.2`，非流式 `chatJson`） |
| Embedding | `text-embedding-v3`，1024 维，版本 `v3`（复用 M4 检索） |
| API Key | 仅后端环境变量 `DASHSCOPE_API_KEY`；前端、Git、日志均不含密钥 |
| AI 调用日志 | `log_ai_call`，`biz_type`：4-计划生成 / 5-出题 / 6-简答判分 |

集中配置（不散落业务代码）：

`studypilot.plan`（`PlanProperties`）

| 配置 | 值 | 含义 |
|---|---|---|
| `retrieval-top-k` | 6 | 计划生成检索条数 |
| `retrieval-min-score` | 0.45 | 最低相似度阈值 |
| `max-context-chars` | 6000 | 检索上下文字符预算 |
| `max-tasks` | 60 | 单次生成任务数上限 |
| `max-generation-ms` | 180000 | 单次生成硬 deadline |

`studypilot.quiz`（`QuizProperties`）

| 配置 | 值 | 含义 |
|---|---|---|
| `retrieval-top-k` | 8 | 出题检索条数 |
| `retrieval-min-score` | 0.45 | 最低相似度阈值 |
| `max-context-chars` | 8000 | 检索上下文字符预算 |
| `min-questions` / `max-questions` | 1 / 20 | 题目数量钳制范围 |
| `default-question-count` | 5 | 默认题数 |
| `short-answer-pass-score` | 60 | 简答及格线（≥60 记为正确） |
| `max-generation-ms` | 180000 | 单次生成硬 deadline |

## 2. 数据库变更（仅 ALTER 增列，无新表、无重建）

迁移脚本 `sql/migration-m6.sql` 已执行；`sql/init.sql` 同步增列。

| 表 | 新增列 |
|---|---|
| `biz_study_plan` | `kb_id`、`topic`、`daily_minutes`、`current_level`、`expected_outcome`、`generate_status`、`generate_error` |
| `biz_study_task` | `user_id`、`stage`、`focus`、`recommended_content`、`stage_quiz` |
| `biz_quiz` | `topic`、`question_types`、`generate_status`、`generate_error` |
| `biz_quiz_question` | `difficulty`、`knowledge_point`、`source_doc_id`、`source_content`、`score`、`score_basis` |

`generate_status`：0-NONE / 1-GENERATING / 2-SUCCESS / 3-FAILED（`GenerateStatus` 枚举）。
错题回顾**不新建表**：由 `biz_quiz_question`（`is_correct=0` 或 简答 `score<60`）关联本人已交卷测验（`status=1`）查询导出。

## 3. 模块拆分（数据/所有权层共享，避免循环依赖）

| 模块 | 职责 |
|---|---|
| `StudyPlanService` | 计划 CRUD、所有权校验、进度重算、`replaceTasks` 事务落库、知识库归属校验（共享层） |
| `StudyTaskService` | 任务列表/状态更新/编辑，更新后触发计划进度重算 |
| `StudyPlanGenerator` | 异步生成编排：Redis 防重锁 → 检索（kbId>0）→ Prompt → `chatJson`(biz4) → 解析 → 校验 → 落库；失败落 FAILED 终态 |
| `PlanPromptBuilder` / `PlanOutputValidator` | 计划系统/用户提示词；任务结构化校验（日期范围、数量上限、字段截断） |
| `QuizStoreService` | 测验数据/所有权/VO 层（共享）：shell 创建、`replaceQuestions` 事务、练习模式隐藏答案、结果/错题 VO |
| `QuizGenerator` | 异步出题编排：锁 → 检索（空内容明确报错）→ `chatJson`(biz5) → 解析 → 校验 → 落库 |
| `QuizPromptBuilder` / `QuizOutputValidator` | 出题提示词（答案编码规则）；题数/题型/答案合法性/**引用真实性**校验，映射真实 chunkId/docId/原文 |
| `QuizScoringService` | 交卷判分：客观题集合匹配（多选严格全对）、简答 AI 评分(biz6)+依据、平均分落库 |
| `QuizService` | 出题/列表/详情/状态/交卷/结果/删除/错题 编排 |
| `AiOutputParser` | 去 ``` 围栏 + Jackson 反序列化，失败抛 `AI_SERVICE_ERROR`（绝不保存不可解析输出） |
| `DashScopeClient`（扩展） | 新增 `chatJson(userId,bizType,system,user)` 重载，`log_ai_call` 记录 bizType |

异步：`@Async("planQuizExecutor")`（core2/max2/queue50/AbortPolicy，前缀 `plan-quiz-gen-`）；`TaskRejectedException` → FAILED 终态；Redis 锁 TTL 15min 防重复生成。

## 4. 后端接口（JWT 走 Authorization 头，统一 `Result` 包装）

学习计划 `/api/study-plans`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `` | 创建学习目标 |
| GET | `` | 本人计划列表 |
| GET | `/{id}` | 计划详情 |
| PUT | `/{id}` | 更新计划 |
| DELETE | `/{id}` | 删除计划（级联任务） |
| POST | `/{id}/generate` | 提交 AI 生成（即时返回状态） |
| GET | `/{id}/generate-status` | 轮询生成状态 |
| GET | `/{id}/tasks` | 每日任务列表 |
| GET | `/{id}/progress` | 完成进度 |

学习任务 `/api/study-tasks`：`PUT /{id}/status`（完成/取消）、`PUT /{id}`（编辑）。

智能出题 `/api/quizzes`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/generate` | 提交出题（即时返回） |
| GET | `` | 本人测验列表 |
| GET | `/wrong-answers` | 错题回顾 |
| GET | `/{id}` | 测验详情（练习模式隐藏答案/解析/原文） |
| GET | `/{id}/generate-status` | 轮询生成状态 |
| POST | `/{id}/submit` | 交卷判分 |
| GET | `/{id}/result` | 成绩与逐题解析（须已交卷） |
| DELETE | `/{id}` | 删除测验 |

## 5. AI 结果校验（不直接信任大模型输出）

1. 结构化 DTO：`StudyPlanGenerateResult` / `QuizGenerateResult` / `ShortAnswerScoreResult`（`@JsonIgnoreProperties` + `normalize()`）。
2. JSON 解析与字段校验：`AiOutputParser` 去围栏 + 反序列化，失败明确报错。
3. 题目数量校验：实际题数必须等于配置题数。
4. 答案合法性：单选 `^[A-Z]$` 且在选项范围；多选 ≥2 个不重复字母（排序归一）；判断固定 2 选项且答案 A/B；简答参考答案非空。
5. 引用真实性：模型仅给 `sourceIndex`，后端映射检索到的真实 chunkId/docId/原文；null/越界 → 「涉嫌伪造来源」整批失败。
6. 生成失败返回明确错误并落 FAILED 终态。
7. 任一题非法 → 整批失败，**绝不保存部分/不可解析题目**。

## 6. 测试结果

### 6.1 单元测试（真实运行，`mvn -o test`）— 46 项全过

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `QuizOutputValidatorTest` | 12 | 全题型合法映射真实来源、题数不符、空题、单选越界、多选<2、判断选项数、简答空、伪造/null sourceIndex、题型越界、空题干、空检索 |
| `PlanOutputValidatorTest` | 9 | 合法任务映射、空任务、空标题、日期早于/晚于范围、日期格式非法、超任务上限、时长钳制、阶段测验标记 |
| `VectorMathTest` | 9 | 余弦检索（M4 回归） |
| `ChunkServiceTest` | 8 | 切片（M4 回归） |
| `ConversationHistoryTest` | 3 | 历史预算（M5 回归） |
| `RagPromptBuilderTest` | 5 | 反伪造提示词（M5 回归） |

合计 **46 项，Failures: 0，Errors: 0，Skipped: 0**。其中 M6 新增 21 项校验测试覆盖「结构化输出异常 / 不同题型答案校验」。

### 6.2 真实集成测试（`test-assets/m6-test.mjs`，直查 MySQL 断言）

非 AI 部分（M6 后端 8081 真实运行）— **10 项全过**：

| 编号 | 用例 | 结果 |
|---|---|---|
| P1/P1b | 正常创建学习目标 + 字段回显 | PASS |
| P3 | 计划持久化到 MySQL（直查 + 重新 GET） | PASS |
| P7 | 用户越权访问/修改/删除计划 → 404 | PASS |
| P6 | AI 调用失败返回明确错误并落 FAILED 终态（`generate_status=3`） | PASS |
| Q0/Q0b | 创建知识库 + 提交出题即时返回 | PASS |
| Q0c | 出题失败落终态 | PASS |
| Q8/Q8b | 用户数据隔离（越权 404、列表不泄漏、错题为空、归属正确） | PASS |

AI 依赖部分 — **9 项 SKIP（待密钥，绝不伪造）**：P2 AI 生成计划、P4 任务完成状态修改、P5 计划进度计算、Q1 正常生成题目、Q3 不同题型答案校验（真实题）、Q4 客观题自动判分、Q5 简答题 AI 评分、Q6 练习记录持久化、Q7 错题记录查询。
脚本已内置自动探测：后端配置密钥后运行 `node m6-test.mjs http://localhost:8080/api` 即真实执行上述全部用例。

### 6.3 前端验证

- `vue-tsc` 类型检查：通过（修复 PlanView `@change` 隐式 any）。
- 生产构建：通过（PlanView 15.92kB、QuizView 16.10kB）。
- 浏览器端到端：待主后端以 M6 代码 + 密钥重启后验证（前端 `/api` 代理指向 8080）。

## 7. 已知问题 / 待办

1. **真实 AI 验证待密钥（阻塞项）**：真实 `DASHSCOPE_API_KEY` 仅存于运行 M5 代码的主后端进程（PID 136808，端口 8080）环境中；当前会话无法从文件/转录/持久环境变量恢复，且不得终止该进程（会永久丢失密钥）。需用户以 M6 代码 + 密钥重启主后端（8080），随后补全 §6.2 的 9 项 AI 集成测试与浏览器端到端验证。
2. **密钥轮换**：M3 起聊天中暴露过的 DashScope API Key 仍建议轮换（后端环境变量更新即可，前端/Git 不含密钥）。
3. `application.yml` 既有 `rag:` 块缩进位于 `logging:` 下（M5 遗留，默认值恰好一致），非 M6 范围，未改动。
4. M1 遗留 11 表注释 mojibake 修复待批准，非 M6 范围。

## 8. 交付物清单

- 迁移：`sql/migration-m6.sql`（已执行）、`sql/init.sql`（同步）。
- 后端：实体/Mapper ×4、`GenerateStatus`、`PlanProperties`/`QuizProperties`、DTO/VO、`PlanPromptBuilder`/`QuizPromptBuilder`、`AiOutputParser`/`PlanOutputValidator`/`QuizOutputValidator`、`StudyPlanService`/`StudyTaskService`/`StudyPlanGenerator`、`QuizStoreService`/`QuizGenerator`/`QuizScoringService`/`QuizService`、3 控制器、`AsyncConfig` 线程池、`DashScopeClient` 扩展。
- 前端：`types/api.ts`、`api/plan.ts`、`api/quiz.ts`、`views/plan/PlanView.vue`、`views/quiz/QuizView.vue`。
- 测试：`QuizOutputValidatorTest`/`PlanOutputValidatorTest`、`test-assets/m6-test.mjs`。
- 报告：本文件。

**M6 实现完成；按里程碑门禁暂停，等待用户确认（含密钥恢复以补全真实 AI 验证），不进入 M7。**