# M7-6 自动化测试报告

> 阶段：M7 系统整合与比赛交付 · 子任务 M7-6
> 执行日期：2026-11-18
> 环境：真实后端 Spring Boot :8080（jar 运行中）+ 真实 MySQL 8（studypilot）+ 真实 Redis + 真实 DashScope（qwen-plus / text-embedding-v3）+ 前端 Vite dev :5173。
> 原则：以下全部为实际执行的命令与真实输出；未执行的测试不写成通过；无虚构指标。

## 一、测试汇总

| 套件 | 命令 | 结果 | 断言/用例 |
|------|------|------|-----------|
| 后端单元测试 | `mvn -o test` | BUILD SUCCESS | 46 / 46 通过，0 失败 0 错误 0 跳过 |
| 后端打包 | `mvn -o package -DskipTests` | BUILD SUCCESS | spring-boot repackage 生成可执行 jar |
| 前端类型检查 | `npm run typecheck`（vue-tsc --noEmit） | 通过 | 无类型错误 |
| 前端生产构建 | `npm run build`（vite build） | 通过 | ✓ built in 11.44s |
| 工作台统计接口 | `node m7-dashboard-test.mjs` | PASS=42 FAIL=0 | 42 项真实 DB 统计断言 |
| 全项目接口联调 | `node m7-integration-test.mjs` | PASS=33 FAIL=0 | 16 步端到端真实链路 |
| 安全检查 | `node m7-security-test.mjs` | PASS=55 FAIL=0 | JWT/上传/隔离/级联/单会话/限流 |
| 性能探针 | `node m7-perf-probe.mjs` | 真实测量完成 | 检索/overview/分页上限 |

合计自动化断言：46（单元）+ 42（工作台）+ 33（联调）+ 55（安全）= **176 项全部通过**。

## 二、后端单元测试明细（mvn -o test）

```
VectorMathTest            Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0
ChunkServiceTest          Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0
ConversationHistoryTest   Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0
PlanOutputValidatorTest   Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0
QuizOutputValidatorTest   Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
RagPromptBuilderTest      Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0
Results: Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS  Total time: 11.734 s
```

覆盖：向量余弦/边界（null/维度/NaN/零范数）、文本切片（目标长度/重叠/边界）、会话历史拼装、计划与出题 AI 输出校验、RAG Prompt 引用组装。M7-3 新增的 Prompt 注入隔离规则未破坏 RagPromptBuilderTest（5/5 通过）。

## 三、后端打包（mvn -o package -DskipTests）

```
spring-boot:3.3.5:repackage
Replacing main artifact .../target/studypilot-backend-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
BUILD SUCCESS  Total time: 3.087 s
```

产物：`studypilot-backend/target/studypilot-backend-0.0.1-SNAPSHOT.jar`（可执行 fat jar，当前运行实例即由此 jar 启动）。

## 四、前端类型检查与生产构建

- `npm run typecheck`：vue-tsc --noEmit 无错误输出，退出码 0。
- `npm run build`：`✓ built in 11.44s`，产物输出 dist/。
- 构建提示：element-plus（1086.50 kB / gzip 342.54 kB）、echarts（500.62 kB / gzip 168.24 kB）超 500 kB 警告阈值；二者为第三方库，已 manualChunks 独立分包，属预期提示，非回归。业务视图分包均 < 100 kB。

## 五、工作台统计接口（node m7-dashboard-test.mjs，PASS=42 FAIL=0）

真实 MySQL 统计（demo 账号）：
- overview：知识库 2 / 论文文档 15 / 已解析 13 / 已向量化 5 / 问答会话 2 / 学习计划 4 / 已完成任务 1 / 练习次数 4。
- document-statistics：vectorDist {未向量化 9, 向量化中 0, 已向量化 5, 向量化失败 1}；uploadTrend 7 天。
- learning-statistics：planTotal 4 / taskTotal 42 / completedTaskTotal 1 / avgProgress 2；planProgress 合计 42、完成合计 1；activityTrend 4 系列 × 7 天。
- quiz-statistics：quizTotal 6 / practicedTotal 4 / avgScore 30；scoreDist 5 桶合计=4；typeDist {单选 11, 多选 4, 判断 6, 简答 4}；practiceTrend 7 天。

所有数值与数据库一致，无记录处显示 0 / 诚实空状态，无模拟数据。

## 六、全项目接口联调（node m7-integration-test.mjs，PASS=33 FAIL=0）

真实端到端 16 步（用户 m7_1794935190759，真实 DashScope）：

| 步 | 验证点 | 真实结果 |
|----|--------|----------|
| 01-02b | 注册→登录→/users/me | code 200，username 一致 |
| 03 | 创建知识库 | id>0 |
| 04 | 上传 PDF | id>0 |
| 05-05b | PDFBox 解析 | status=SUCCESS，chars=3749，pages>0 |
| 06-06c | AI 论文分析（qwen-plus） | status=SUCCESS，12 字段，title=FedLite: Communicati… |
| 07-08e | 切片→Embedding 入库→检索 | chunks=16 emb=16，DB 持久化 16，doc status=4/vector=2，search n=3 top=0.6399 |
| 09-10b | 会话→SSE→RAG→引用 | SSE bytes=7078，答案持久化 SUCCESS len=490，citations n=4 docId 匹配，originalContent 非空 |
| 11-11d | 创建计划→AI 生成→任务 | generate SUCCESS，tasks n=7 |
| 12-12b | 完成任务→进度重算 | progress=14 |
| 13-13c | AI 出题 | generate SUCCESS，questions n=3（练习态隐藏答案） |
| 14-15 | 提交练习→成绩 | score=67，结果揭示分数+解析 |
| 16 | 工作台反映新用户 | documentTotal 1 / kb 1 / parsed 1 / vectorized 1 / conv 1 / plan 1 / completedTask 1 / totalTask 7 / quizPractice 1 / progress 14 |

每一步均落真实数据库，AI 为真实调用（非 Mock）。

## 七、安全检查（node m7-security-test.mjs，PASS=55 FAIL=0）

- A JWT：无 token 401 / 篡改 token 401 / 注册登录正常。
- B 上传校验：无 magic 假 PDF 400 / 错误扩展名 400 / 错误 MIME 400 / 空文件 400 / 超大 32.5MB 400 / 恶意文件名 `../../../../evil.pdf` 被 UUID 重命名（路径取自 DB file_path）。
- D 跨用户隔离 D1-D21：B 访问 A 的文档/内容/解析/删除/向量化/知识库/会话/计划/任务/练习全部被拒（403/404），列表互不可见，B 不能在 A 知识库下建会话/计划/上传。
- E 级联清理 E0-E9：删除文档→逻辑删除 deleted=1 + 切片物理 0 行 + 物理 PDF 删除 + 解析 JSON 删除；删除知识库→级联文档逻辑删除 + 物理 PDF 删除。
- F 单会话：B 重新登录→旧 token 401→新 token 有效。
- G 限流：G1 登录暴力 12 次 codes=400,400,400,400,429,429,429,429,429,429,429,429（auth 桶 10 次/60s 生效）。

注：限流修复前 G1 预期 FAIL；修复后 55/55。详见 docs/M7-security-report.md。

## 八、性能探针（node m7-perf-probe.mjs，真实测量）

- demo KB1 向量切片 N=288，维度 D=1024。
- 语义检索 5 次：496 / 359 / 413 / 399 / 520 ms，平均 437 ms（min 359 / max 520）。主导成本为 Embedding API 往返；N×D≈294912 次浮点乘加，内存余弦计算量可忽略，无常驻索引。
- 工作台 overview 3 次：11 / 14 / 12 ms，平均 12 ms。
- 分页 size=1000 → 实际返回 500（PaginationInnerInterceptor maxLimit=500 生效）。

详见 docs/M7-performance-report.md。向量存储架构未替换（按规范需确认方可变更）。

## 九、失败项与说明

- 本轮所有套件 0 失败。
- 联调首次运行曾在 01/02 失败：原因是紧接安全测试 G1 之后，同一 IP（127.0.0.1）的 auth 限流桶仍在 60s 窗口内导致注册/登录被 429。等待窗口过期（studypilot:ratelimit:* 键自然 TTL 到期，未触碰其他应用键）后重跑，33/33 通过。此为限流正常生效的体现，非功能缺陷。

## 十、结论

M7-6 自动化测试全部实际执行并通过：后端单元 46/46、打包成功、前端 typecheck/build 通过、工作台 42/42、联调 33/33（真实 AI + DB 持久化 + RAG 引用 + 权限隔离）、安全 55/55、性能探针真实测量。无未执行而谎报通过的项目，无虚构指标。