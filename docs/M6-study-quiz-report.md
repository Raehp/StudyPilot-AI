# M6 交付报告：AI 学习计划与智能出题

日期：2026-11-17
范围：学习目标 CRUD → 通义千问异步生成学习阶段/每日任务（可结合指定知识库真实文档）→ 任务完成状态与进度管理（MySQL 持久化）；出题配置 → 基于知识库真实内容异步生成题目（结构化校验 + 引用真实性校验）→ 在线练习 → 客观题自动判分 + 简答题 AI 辅助评分（含评分依据）→ 练习记录持久化 → 历史成绩与错题回顾。
**沿用 M4/M5 已验收的 MySQL JSON + Java 内存余弦检索与 DashScope 封装；未引入新表（仅 4 表 ALTER 增列）；未实现 M7 功能。**

> 状态说明：M6 全部代码已实现并通过编译、前端类型检查与生产构建、**46 项单元测试**、**29 项真实集成测试（PASS=29 / FAIL=0 / SKIP=1）**、**浏览器端到端验证（学习计划 + 智能出题全流程，零控制台报错）**。真实 `DASHSCOPE_API_KEY` 取自仓库外文件 `E:/AI-Qoder-Pro/.secrets/dashscope.key`，仅注入后端进程环境；**17 次真实 qwen-plus 调用（计划 8 / 出题 5 / 简答判分 4）全部成功，绝不以固定题目或模拟成绩冒充真实 AI**。验证过程中发现并修复 2 个真实异步缺陷（见 §7）。按里程碑门禁暂停，等待用户验收，不进入 M7。

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
**异步触发方式**：`StudyPlanGenerator` / `QuizGenerator` 经 `ObjectProvider<Self>` 代理自调用 `generateAsync`，确保 `@Async` 生效（同类内直接调用会绕过 Spring 代理退化为同步，见 §7 缺陷①）；worker 线程入口 `UserContext.setUserId(userId)`、`finally` 中 `clear()`，保证检索所有权过滤可用（见 §7 缺陷②）。

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

合计 **46 项，Failures: 0，Errors: 0，Skipped: 0，BUILD SUCCESS**。其中 M6 新增 21 项校验测试覆盖「结构化输出异常 / 不同题型答案校验」。修复 §7 两个异步缺陷后重跑仍 46/46 全过。

### 6.2 真实集成测试（`test-assets/m6-test.mjs`，M6 后端 8081 + 真实密钥，直查 MySQL 断言）

**结果：PASS=29 / FAIL=0 / SKIP=1。** 覆盖需求规定的学习计划 7 项 + 智能出题 8 项全部用例，AI 依赖项均以真实 qwen-plus 调用执行。

学习计划（7 项必测）

| 必测项 | 对应用例 | 结果 |
|---|---|---|
| 正常创建学习目标 + 字段回显 | P1 / P1b | PASS |
| 计划持久化到 MySQL（直查 + 重新 GET） | P3 | PASS |
| AI 生成学习阶段/每日任务（含结合知识库真实文档） | P2 / P2b / P2c / P2d | PASS |
| 任务完成状态修改（完成/取消） | P4 / P4b | PASS |
| 计划进度计算 | P5 | PASS |
| AI 失败返回明确错误并落 FAILED 终态 | P6 | SKIP（AI 可用时由 Q0c 真实空知识库失败终态覆盖；无密钥轮已 PASS） |
| 用户越权 / 数据隔离 | P7 / P7b | PASS |
| 生成请求即时返回（异步，<5000ms） | P0 | PASS |

智能出题（8 项必测）

| 必测项 | 对应用例 | 结果 |
|---|---|---|
| 正常生成题目（真实 AI，全题型） | Q1 / Q1b | PASS |
| 不同题型答案合法性校验（真实题） | Q3 | PASS |
| 客观题自动判分（多选严格全对） | Q4 | PASS |
| 简答题 AI 评分 + 评分依据 + 参考答案 | Q5 | PASS |
| 练习记录持久化 | Q6 | PASS |
| 错题记录查询 | Q7 | PASS |
| 引用真实性 / 来源文档 / 练习隐藏答案 / 结果展示原文 | Q1c / Q1d / Q1e / Q1f | PASS |
| 失败落终态 / 用户数据隔离 | Q0c / Q8 / Q8b | PASS |
| 提交出题即时返回（异步，<5000ms） | Q0 / Q0b | PASS |

> P6 在「AI 可用」轮被脚本自动 SKIP：该用例验证「AI 不可用→FAILED 终态」，AI 可用时由 Q0c（真实空知识库检索为空→`generate_status=3` 终态）等价覆盖；此前无密钥轮 P6 已真实 PASS。其余 29 项全部真实执行通过。

### 6.3 真实 AI 调用统计（`log_ai_call`，biz_type 4/5/6，全部 status=0 成功）

| biz_type | 用途 | 调用数 | 成功 | 平均耗时 | 最大耗时 | prompt tokens | completion tokens |
|---|---|---|---|---|---|---|---|
| 4 | 学习计划生成 | 8 | 8 | 47.5s | 53.6s | 4,644 | 17,105 |
| 5 | 智能出题 | 5 | 5 | 20.6s | 23.2s | 5,267 | 4,587 |
| 6 | 简答题 AI 判分 | 4 | 4 | 2.3s | 2.55s | 1,380 | 346 |
| 合计 | — | **17** | **17** | — | — | **11,291** | **22,038** |

计划/出题耗时较长（数十秒）正是必须异步化的原因：同步执行会超过前端 axios 15s 超时（见 §7 缺陷①）。所有调用均记录 `log_ai_call`，不含密钥。

### 6.4 浏览器端到端验证（前端 5174 → 后端 8081 真实密钥，修复后重测）

**智能出题全流程（零控制台报错）：**
- 配置知识库「深度学习论文库 v2」+ 主题「联邦学习」+ 5 题 + 基础 + 全 4 题型 → 点击「生成测验」。
- **`POST /api/quizzes/generate` 实测 200 / 39ms 即时返回**（修复前同步阻塞约 18–46s，触发前端「请求超时」）；toast「已加入 AI 出题队列」→ 进度弹窗「AI 正在基于知识库出题…」→ 列表「生成中」。
- 轮询至「已生成」自动进入练习；5 道真实题目（单选/多选/判断/简答）均基于《FedLite》论文真实内容。
- 作答后「交卷」→ 客观题自动判分 + 简答 AI 评分：**综合得分 80，答对 4/5**（单选 C=100、多选 A,B 严格全对=100、判断 A=100、简答=100 且附真实「AI 评分依据」、单选错答 A=0）。
- 「查看原文片段」弹层展示真实检索原文（"1. Introduction … Federated learning (FL) trains a shared model by ex…"），引用真实性可见。
- 「错题回顾」标签即时收录本次错题（本题 0 分 / 你的答案 A / 正确答案 B / 解析 / 来源《paper》· 知识点），由查询导出、无新表。

**学习计划全流程（零控制台报错）：**
- 知识库绑定计划「M6真实知识库计划」渲染 14 项逐日任务 / 5 阶段，每任务含阶段、重点、推荐《paper》具体章节、阶段测验标记、AI 总体建议，均真实 grounded。
- 任务打卡：勾选首项 → 进度 **0/14 → 1/14**；取消勾选 → **1/14 → 0/14**（完成/取消 + 进度重算均生效）。
- 「重新生成」→ 确认弹窗「将覆盖当前任务清单与打卡进度」→ **`POST /api/study-plans/9/generate` 实测 200 / 18ms 即时返回** → 轮询至 SUCCESS → 替换为 14 项全新任务（本次 4 阶段、AI 建议措辞更新，仍真实引用《FedLite》），进度归零。

### 6.5 前端构建验证

- `vue-tsc --noEmit` 类型检查：通过（无错误）。
- `vite build` 生产构建：通过，2282 modules transformed，built in 11.10s；`PlanView` 15.92kB、`QuizView` 16.10kB。

## 7. 验证中发现并修复的真实缺陷

**缺陷① `@Async` 同类自调用退化为同步（阻塞 HTTP 线程 → 前端超时）**
- 现象：浏览器点击「生成测验/生成计划」后请求长时间挂起，最终前端 axios 15s 超时报「请求超时」；`POST …/generate` 实测阻塞约 18–46s。
- 根因：`QuizGenerator.submit` / `StudyPlanGenerator.submit` 直接调用本类 `generateAsync(...)`，绕过 Spring AOP 代理，`@Async` 不生效，生成在 HTTP 请求线程同步执行。
- 修复：注入 `ObjectProvider<Self>`，经 `self.getObject().generateAsync(...)` 代理自调用触发异步（`ObjectProvider` 延迟解析避免循环依赖）。
- 验证：修复后浏览器实测出题 generate **39ms**、计划 generate **18ms** 即时返回；集成测试新增 P0 / Q0b 异步即时返回断言（<5000ms）PASS。与 M4 `DocumentVectorExecutor` 同类缺陷一致。

**缺陷② 异步 worker 线程缺失 `UserContext` 导致检索所有权过滤失败**
- 现象：缺陷①修复后真正异步执行时，结合知识库的生成报「知识库不存在」。
- 根因：`UserContext` 为 `ThreadLocal`，不随 `@Async` 传播到 worker 线程；`DocumentVectorService.search` 依 `UserContext.getUserId()` 做所有权过滤，worker 上为 null。
- 修复：`generateAsync` 入口 `UserContext.setUserId(userId)`，`finally` 中 `UserContext.clear()`（两个生成器一致）。
- 验证：P2d（计划结合知识库真实文档）、Q1（真实出题）及浏览器计划/出题全流程均真实 grounded 成功。

## 8. 已知问题 / 待办

1. **密钥轮换**：M3 起聊天中暴露过的 DashScope API Key 仍建议轮换（后端环境变量更新即可，前端/Git 不含密钥）。当前真实密钥取自仓库外 `E:/AI-Qoder-Pro/.secrets/dashscope.key`。
2. `application.yml` 既有 `rag:` 块缩进位于 `logging:` 下（M5 遗留，默认值恰好一致），非 M6 范围，未改动。
3. M1 遗留 11 表注释 mojibake 修复待批准，非 M6 范围。
4. 计划/出题单次生成耗时数十秒（真实大模型 + 检索），已异步化 + 轮询 + Redis 防重锁；线程池 core2/max2/queue50，超出并发以 `TaskRejectedException` → FAILED 终态明确提示「生成任务繁忙」。

## 9. 交付物清单

- 迁移：`sql/migration-m6.sql`（已执行）、`sql/init.sql`（同步）。
- 后端：实体/Mapper ×4、`GenerateStatus`、`PlanProperties`/`QuizProperties`、DTO/VO、`PlanPromptBuilder`/`QuizPromptBuilder`、`AiOutputParser`/`PlanOutputValidator`/`QuizOutputValidator`、`StudyPlanService`/`StudyTaskService`/`StudyPlanGenerator`、`QuizStoreService`/`QuizGenerator`/`QuizScoringService`/`QuizService`、3 控制器、`AsyncConfig` 线程池、`DashScopeClient` 扩展。
- 前端：`types/api.ts`、`api/plan.ts`、`api/quiz.ts`、`views/plan/PlanView.vue`、`views/quiz/QuizView.vue`。
- 测试：`QuizOutputValidatorTest`/`PlanOutputValidatorTest`、`test-assets/m6-test.mjs`。
- 缺陷修复：`StudyPlanGenerator` / `QuizGenerator`（`ObjectProvider` 代理自调用 + `UserContext` 传播）。
- 报告：本文件。

**M6 实现完成，全部真实测试与浏览器端到端验证通过；按里程碑门禁暂停，等待用户验收，不进入 M7。**