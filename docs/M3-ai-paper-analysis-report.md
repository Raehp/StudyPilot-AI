# M3 交付报告：AI 论文智能阅读

日期：2026-11-17 ｜ 状态：**已完成，等待验收确认（未进入 M4）**

## 1. AI 接入方案与依赖

### 1.1 兼容性结论（开发前检查）
- Spring AI Alibaba GA 1.0.x 要求 Spring Boot **3.4+**，1.1.x 要求 3.4/3.5（官方版本矩阵 java2ai.com/docs/versions）。
- 本项目锁定 **Spring Boot 3.3.5 + Java 17**，规格不允许随意升级 Boot。
- 仅旧里程碑版（1.0.0-M3.2/M5.1）兼容 3.3，但需引入 Spring Milestone 仓库且不稳定。
- **按规格允许的"经过封装的后端服务"路线**，经用户确认采用：RestClient 直连 DashScope OpenAI 兼容端点，**零新增依赖**，pom.xml 仅更新说明注释。

### 1.2 接入参数
| 项 | 值 |
|---|---|
| 端点 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| 模型 | **qwen-plus**（通义千问） |
| 输出约束 | `response_format: {type: "json_object"}`，temperature 0.2 |
| 连接超时 | 10s（`connect-timeout-ms`） |
| 读超时 | 120s（`read-timeout-ms`） |
| 并发限制 | Semaphore 2（`max-concurrent`），tryAcquire 30s |
| 分段阈值 | 6000 字符/段（`segment-chars`），最多采样 12 段（`max-segments`） |

### 1.3 密钥安全
- API Key 仅从环境变量 `DASHSCOPE_API_KEY` 读取（`application.yml` 占位符 `${DASHSCOPE_API_KEY:}`）。
- 未写入任何代码、前端、Git 文件；测试期密钥存放于仓库外 `E:/AI-Qoder-Pro/.secrets/dashscope.key`，启动时注入。
- 后端日志与 `log_ai_call` 表经核验**不含密钥**（grep 0 命中）。
- ⚠️ 该 Key 曾在对话中明文出现，**建议 M3 验收后在阿里云控制台禁用并重建**。

## 2. 数据库

新表 `biz_paper_analysis`（迁移脚本 `sql/migration-m3.sql`，已执行；`sql/init.sql` 同步为第 ⑫ 张表）：

| 字段 | 说明 |
|---|---|
| document_id / user_id | 文档与归属用户 |
| model | 模型名称（qwen-plus） |
| status | 0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED |
| error_msg | 脱敏错误信息 |
| result (json) / summary | 12 字段结构化结果 / 摘要冗余 |
| duration_ms / segment_count | 耗时 / 分段数（0=单段直析） |
| create_time / update_time / deleted | 审计与逻辑删除 |

**重新分析语义（用户确认方案）**：每次分析新增任务行，历史全部保留；最新 SUCCESS 任务为当前展示结果，即**重新分析覆盖展示、不删历史**。前端弹窗明示"重新分析将覆盖当前展示的分析结果（历史任务记录保留）"。

## 3. 后端实现

| 组件 | 职责 |
|---|---|
| `DashScopeProperties` | `studypilot.ai.*` 集中配置 |
| `DashScopeClient` | RestClient 封装：超时/并发 Semaphore/错误映射/`log_ai_call` 落库（M1 预留表，biz_type=2，记录 tokens、耗时、状态 0成功/1失败/2超时/3限流，不含密钥） |
| `PaperAnalysisService` | `@Async("analysisExecutor")`（core2/max2/queue50）；读解析文本 → 分段 map-reduce → JSON 校验/去围栏/normalize → 落库 + 回写文档 analyzed/summary |
| `PaperAnalysisTaskService` | 前置校验 + Redis SETNX 锁（TTL 15min）+ 活跃任务计数 + 任务行 CAS，防重复/并发 |
| `PaperController` | 3 个 API（见 §4） |

### 3.1 分析规则
- 仅 **解析成功（READY）且有效文本 >0、非扫描版** 的文档可发起分析。
- 12 字段：论文标题/摘要/研究背景/研究问题/研究方法/技术路线/核心创新点/实验设计/实验结果/研究结论/研究局限性/未来研究方向。
- **防编造**：System Prompt 强制"原文无依据的字段输出『未明确说明』"；`PaperAnalysisResult.normalize()` 对空值兜底填充。实测合成文本与缺失章节均诚实标记（见 §6）。
- **超长论文**：>6000 字符分段；>12 段时均匀采样（含首尾段）；逐段提取（坏 JSON 段跳过）→ 归并reduce。**绝不整篇塞入单次 Prompt**。94 万字符实测仅 13 次调用（12 map + 1 reduce）。

### 3.2 错误映射（全部脱敏为 BusinessException 5001）
| 场景 | 用户提示 | log 状态 |
|---|---|---|
| Key 缺失 | AI 服务未配置 API Key，请设置环境变量 DASHSCOPE_API_KEY | 任务 FAILED |
| HTTP 429 | AI 服务限流，请稍后重试 | 3 |
| HTTP 401/403 | AI 服务认证失败，请检查 API Key 配置 | 1 |
| HTTP 5xx | AI 服务暂时不可用，请稍后重试 | 1 |
| 读/连接超时 | AI 服务响应超时，请稍后重试 | 2 |
| 网络异常 | AI 服务网络异常，请稍后重试 | 1 |
| 并发满/线程池拒绝 | AI 服务繁忙 / 分析任务繁忙，请稍后重试（429） | — |

## 4. API（沿用 Result 包装与认证约定）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/papers/{id}/analyze` | body 可选 `{reanalyze: true}`；已 SUCCESS 未带 flag → 400 覆盖确认提示 |
| GET | `/api/papers/{id}/analysis` | 最新 SUCCESS 结果；无则 404 |
| GET | `/api/papers/{id}/analysis-status` | 最新任务状态；从未分析 → status=null |

## 5. 前端（PaperReadingView 重写）

- 左侧论文列表（状态点/需OCR 标记），右侧阅读区。
- AI 分析/重新分析按钮（不可分析时禁用 + 原因 tooltip）；PENDING/PROCESSING 进度卡（3s 轮询）；FAILED 错误条 + 重试。
- SUCCESS：渐变摘要卡 + 10 张字段卡（方法/创新/实验结果/结论高亮，"未明确说明"弱化斜体）+ 页脚（模型/耗时/单段直析或分段采样 N 段/分析时间/覆盖提示）。
- 重新分析确认弹窗明示覆盖语义；原文折叠面板懒加载逐页文本。
- 浏览器实测：摘要卡/字段卡/页脚/弹窗文案/取消不触发请求，控制台零报错；`vue-tsc` + `vite build` 通过。

## 6. 真实 AI 测试（qwen-plus 实际调用，无 Mock）

累计：**16 个分析任务（5 SUCCESS / 11 FAILED），24 次 API 调用（17 成功 / 4 失败 / 1 超时 / 2 限流），prompt 23180 tokens / completion 2883 tokens**，全部落库 `log_ai_call` 可查。

| # | 规格测试项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 正常论文分析 | ✅ | 仿真论文 paper.pdf（5页/3749字符，FedLite 联邦学习）：SUCCESS 18.5s，12 字段准确提取（94.2%/87.3%/1.6x/k_t 公式等事实核对 7/7 PASS）；故意缺失的"未来研究方向"诚实返回**未明确说明** |
| 2 | 无解析文本 | ✅ | 扫描版/0 字符 → 400"文档无有效文本（扫描版需 OCR）"；未解析成功 → 400（G2/G3） |
| 3 | 超长论文 | ✅ | big.pdf 300页/**940080 字符** → 均匀采样 12 段 map-reduce，SUCCESS **31.0s**，segmentCount=12；合成重复文本无论文信息 → 12 项全部"未明确说明"（防编造在超长输入下仍生效） |
| 4 | API Key 缺失 | ✅ | 无 Key 运行期真实提交：任务 1-3 FAILED，错误信息明确指向 DASHSCOPE_API_KEY（DB 可查） |
| 5 | API 调用超时 | ✅ | 读超时 2s + 慢响应端点 → FAILED"AI 服务响应超时"，log 状态 2（错误路径桩测试，见下方说明） |
| 6 | API 限流 | ✅ | 429 响应 → FAILED"AI 服务限流，请稍后重试"，log 状态 3（桩测试）；真实并发重复提交 → 429"分析正在进行中"。注：DashScope 真实配额 429 难以人为触发，未实测提供商侧限流 |
| 7 | 重复分析请求 | ✅ | PROCESSING 中并发提交 → 429；已 SUCCESS 无 reanalyze → 400 覆盖确认；reanalyze=true → 新任务（doc19 任务 5→6，历史保留，最新覆盖展示） |
| 8 | 用户越权读取 | ✅ | lisi 访问 demo 的文档：读结果/读状态/发起分析均 404（G6 + 重分析脚本 3 项） |

补充验证：失败后重试（doc5 超时 FAILED → 恢复配置后真实重试 SUCCESS 2.7s）；未分析语义（status=null + analysis 404）；未认证 401；不存在文档 404。**防护回归 8/8 PASS**。

> **错误路径测试说明**：超时/限流/5xx/401 四项通过本地桩端点（`m3-error-stub.mjs`，临时将 base-url 指向 localhost:9099）验证客户端异常映射与落库状态，**属异常处理测试，非真实 AI 分析**；正常分析、超长分析、重试均为 DashScope 真实调用。测试后已恢复真实配置并回归验证。

### 6.1 测试中发现并修复的问题
- **超时异常映射遗漏**：RestClient 将读超时包装为 `RestClientException: Error while extracting response`（而非 ResourceAccessException），原 catch 未覆盖导致落入通用错误。已修复为捕获 `RestClientException` 并遍历 cause 链识别 `SocketTimeoutException`，修复后超时测试 PASS。

## 7. 构建与遗留事项

- 后端 `mvn compile` / spring-boot:run 通过；前端 `vue-tsc && vite build` 通过（chunk 体积警告为既有项，非本阶段引入）。
- 测试脚本与资产均在仓库外 `E:/AI-Qoder-Pro/test-assets/`（m3-smoke / m3-paper-test / m3-long-test / m3-reanalyze-test / m3-error-stub / m3-error-test / m3-guard-test / GenPaperPdf）。
- 遗留（不阻塞 M3 验收）：
  1. **API Key 轮换**：对话中暴露过的 Key 建议验收后禁用重建。
  2. M1 遗留 11 张表注释乱码（GBK 导入所致，仅外观问题）——修复脚本已备好，等待确认执行。
  3. M3 代码尚未 Git 提交，等待验收后按阶段提交流程处理。

**M3 到此暂停，等待验收确认，不进入 M4。**