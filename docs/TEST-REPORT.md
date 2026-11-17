# StudyPilot AI 测试报告（TEST-REPORT）

> 比赛交付版 · 执行日期 2026-11-18 · 全部为实际执行的命令与真实输出。
> 环境：真实 Spring Boot :8080 + 真实 MySQL 8（studypilot）+ 真实 Redis + 真实 DashScope（qwen-plus / text-embedding-v3）+ 前端 Vite :5173。
> 原则：未执行的测试不写成通过；无虚构用户数/调用次数/耗时/指标。明细见 [M7-test-report.md](M7-test-report.md)。

## 一、总览

| 套件 | 命令 | 结果 |
|------|------|------|
| 后端单元测试 | `mvn -o test` | **46 / 46 通过**，BUILD SUCCESS |
| 后端打包 | `mvn -o package -DskipTests` | BUILD SUCCESS（可执行 fat jar） |
| 前端类型检查 | `npm run typecheck` | 通过，无类型错误 |
| 前端生产构建 | `npm run build` | 通过，✓ built in 11.44s |
| 工作台统计接口 | `node m7-dashboard-test.mjs` | **PASS=42 FAIL=0** |
| 全项目接口联调 | `node m7-integration-test.mjs` | **PASS=33 FAIL=0** |
| 安全检查 | `node m7-security-test.mjs` | **PASS=55 FAIL=0** |
| 性能探针 | `node m7-perf-probe.mjs` | 真实测量完成 |

自动化断言合计：46 + 42 + 33 + 55 = **176 项全部通过**。

## 二、后端单元测试（46/46）

| 测试类 | 用例数 | 覆盖 |
|--------|--------|------|
| VectorMathTest | 9 | 余弦相似度 + null/维度/NaN/零范数边界 |
| ChunkServiceTest | 8 | 切片目标长度/重叠/边界 |
| ConversationHistoryTest | 3 | 会话历史拼装 |
| PlanOutputValidatorTest | 9 | 计划 AI 输出校验 |
| QuizOutputValidatorTest | 12 | 出题 AI 输出校验 |
| RagPromptBuilderTest | 5 | RAG Prompt 引用组装（含 M7 注入隔离规则后仍通过） |

`Results: Tests run: 46, Failures: 0, Errors: 0, Skipped: 0`。

## 三、全项目接口联调（33/33，真实 AI + DB）

真实端到端 16 步（用户 m7_1794935190759）：
- 注册→登录→/users/me：code 200，身份一致。
- 创建知识库→上传 PDF：id>0。
- PDFBox 解析：SUCCESS，chars=3749，pages>0。
- AI 论文分析（qwen-plus）：SUCCESS，12 字段，title=FedLite: Communicati…。
- 切片→Embedding：chunks=16 emb=16，DB 持久化 16，doc status=4/vector=2；语义检索 n=3 top=0.6399。
- 会话→SSE→RAG→引用：SSE bytes=7078，答案持久化 SUCCESS len=490，citations n=4（docId 匹配，originalContent 非空）。
- 创建计划→AI 生成→任务：SUCCESS，tasks n=7；完成任务 progress=14。
- AI 出题：SUCCESS，questions n=3（练习态隐藏答案）；提交练习 score=67；结果揭示分数+解析。
- 工作台反映新用户：documentTotal 1 / kb 1 / parsed 1 / vectorized 1 / conv 1 / plan 1 / completedTask 1 / totalTask 7 / quizPractice 1 / progress 14。

每一步均落真实数据库，AI 为真实调用（非 Mock）。

## 四、安全检查（55/55）

- JWT：无 token 401 / 篡改 401。
- 上传：假 PDF（无 magic）400 / 错误扩展名 400 / 错误 MIME 400 / 空文件 400 / 超大 32.5MB 400 / 恶意文件名 `../../../../evil.pdf` 被 UUID 重命名。
- 跨用户隔离 D1–D21：B 访问 A 的文档/内容/解析/删除/向量化/知识库/会话/计划/任务/练习全部被拒（403/404），列表互不可见。
- 级联清理 E0–E9：删除文档→deleted=1 + 切片 0 行 + 物理 PDF 删除 + 解析 JSON 删除；删除知识库→级联文档逻辑删除 + 物理清理。
- 单会话 F1–F3：重新登录→旧 token 401→新 token 有效。
- 限流 G1：登录暴力 12 次 codes=400×4,429×8（auth 桶 10 次/60s 生效）。

详见 [M7-security-report.md](M7-security-report.md)。

## 五、工作台统计（42/42，真实 MySQL）

demo 账号真实统计：知识库 2 / 论文文档 15 / 已解析 13 / 已向量化 5 / 问答会话 2 / 学习计划 4 / 已完成任务 1 / 练习次数 4；向量化分布 {未 9, 中 0, 已 5, 失败 1}；学习 taskTotal 42 / completed 1；测验 quizTotal 6 / practiced 4 / avgScore 30，成绩分布 5 桶合计=4，题型 {单选 11, 多选 4, 判断 6, 简答 4}。所有数值与 DB 一致，无模拟数据。

## 六、性能探针（真实测量）

- demo KB1 向量切片 N=288，维度 D=1024。
- 语义检索 5 次：496/359/413/399/520 ms，平均 **437ms**（主导为 Embedding API 往返；余弦计算量≈29.5 万次浮点乘加，可忽略）。
- 工作台 overview 3 次：11/14/12 ms，平均 **12ms**。
- 分页 size=1000 → 实际 500（maxLimit=500 生效）。

详见 [M7-performance-report.md](M7-performance-report.md)。向量存储架构未替换。

## 七、前端验证

12 项检查通过（真实 API/加载/空/错误/成功反馈/表单校验/路由权限/明暗主题/响应式/控制台无错误/刷新恢复/429 提示）。实测知识库空名提交→"请输入知识库名称"；主题切换持久化；刷新→401→重登录恢复；逐页控制台无 error/warn。开发期里程碑残留文案已清理。详见 [M7-frontend-report.md](M7-frontend-report.md)。

## 八、失败项与说明

- 本轮所有套件 0 失败。
- 联调首次运行曾在注册/登录失败：紧接安全测试 G1 后同一 IP 的 auth 限流桶仍在 60s 窗口内导致 429；窗口过期后重跑 33/33。属限流正常生效，非缺陷。
- 前端构建提示 element-plus/echarts vendor chunk >500kB：第三方库已分包，属预期，非回归。

## 九、部署验证状态

- 开发机（Windows）**未安装 Docker**，本轮未实际执行 `docker compose up`；已交付完整部署产物（Dockerfile×2 / nginx.conf / docker-compose.yml / .env.example）并通过 YAML 结构校验，部署指南见 [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md)。
- 实际容器部署结果需在具备 Docker 的目标主机执行后据实记录，**不虚构部署地址与运行指标**。

## 十、结论

后端单元 46/46、打包成功、前端 typecheck/build 通过、工作台 42/42、联调 33/33（真实 AI + DB 持久化 + RAG 引用 + 权限隔离）、安全 55/55、性能真实测量。所有结论基于真实运行的系统，无伪造。