# StudyPilot AI 接口文档（API-DOCUMENTATION）

> 与实际控制器映射一致。Base URL：`http://<host>:8080`（开发）或经前端 Nginx `/api` 反代（生产）。
> 认证：除注册/登录/健康检查外，均需请求头 `Authorization: Bearer <token>`。

## 一、统一响应与错误码

**所有接口（含错误）返回 HTTP 200**，业务语义在 body：
```json
{ "code": 200, "message": "success", "data": ... }
```
| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 / 上传校验失败 / 文件超限 |
| 401 | 未认证 / 令牌失效（单会话互踢） |
| 403 | 无权限（跨用户访问） |
| 404 | 不存在（文档/知识库跨用户亦返回 404 以隐藏存在性） |
| 405 | 方法不允许 |
| 429 | 请求过于频繁（限流） |
| 500 | 服务器错误 |
| 5001 | AI 服务错误 |

限流（Redis 固定窗口，按 IP+路由桶，仅 POST）：登录/注册 10 次/60s，AI 端点 30 次/60s，上传 30 次/60s。超限返回 `code:429, message:"请求过于频繁，请稍后重试"`。

分页：列表接口支持 `page`/`size`，`size` 上限 500（超过按 500 返回）。

## 二、认证与健康

| 方法 | 路径 | 认证 | 请求体 | 说明 |
|------|------|------|--------|------|
| POST | `/api/auth/register` | 否 | `{username, password(6-32), nickname?}` | 注册，返回 `{token, user}` |
| POST | `/api/auth/login` | 否 | `{username, password}` | 登录，返回 `{token, user}`；单会话使旧令牌失效 |
| POST | `/api/auth/logout` | 是 | — | 登出，删除 Redis 令牌 |
| GET | `/api/users/me` | 是 | — | 当前用户信息 |
| GET | `/api/health` | 否 | — | `{application, status:"UP"}` |
| GET | `/actuator/health` | 否 | — | Actuator 健康端点 |

## 三、知识库

| 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|-------------|------|
| POST | `/api/knowledge-bases` | `{name(≤64,必填), description?(≤255)}` | 创建 |
| GET | `/api/knowledge-bases` | `page,size,keyword?` | 分页列表（仅本人） |
| GET | `/api/knowledge-bases/{id}` | — | 详情 |
| PUT | `/api/knowledge-bases/{id}` | `{name, description?}` | 更新 |
| DELETE | `/api/knowledge-bases/{id}` | — | 删除（级联清理文档与物理文件） |
| POST | `/api/knowledge-bases/{id}/documents` | `{documentId}` | 将文档移入知识库 |
| DELETE | `/api/knowledge-bases/{id}/documents/{documentId}` | — | 从知识库移除文档 |

## 四、文档与解析

| 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|-------------|------|
| POST | `/api/documents/upload` | multipart：`file`(PDF), `kbId` | 上传（四重校验+UUID 重命名，≤30MB） |
| GET | `/api/documents` | `page,size,kbId?` | 分页列表 |
| GET | `/api/documents/{id}` | — | 详情（**不含 file_path**） |
| DELETE | `/api/documents/{id}` | — | 删除（逻辑删除+切片物理清理+PDF/解析 JSON 删除） |
| GET | `/api/documents/{id}/parse-status` | — | 解析状态 |
| POST | `/api/documents/{id}/parse` | — | 触发/重试解析 |
| GET | `/api/documents/{id}/content` | — | 解析全文（charCount + pages） |

## 五、AI 论文分析

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/api/papers/{id}/analyze` | `{reanalyze?}` | 提交 AI 分析（qwen-plus，异步） |
| GET | `/api/papers/{id}/analysis` | — | 分析结果（12 字段结构化 + 摘要） |
| GET | `/api/papers/{id}/analysis-status` | — | 分析状态 PENDING/PROCESSING/SUCCESS/FAILED |

## 六、向量化与检索

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/api/documents/{id}/vectorize` | `{reprocess?}` | 切片+Embedding（异步，text-embedding-v3 1024 维） |
| GET | `/api/documents/{id}/vector-status` | — | 向量化状态 |
| POST | `/api/knowledge-bases/{kbId}/search` | `{query(必填), topK?}` | 语义检索（按 user+kb 余弦 Top-K） |

## 七、RAG 智能问答

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/api/chat/sessions` | `{kbId(必填), title?}` | 创建会话 |
| GET | `/api/chat/sessions` | `page,size` | 会话列表 |
| GET | `/api/chat/sessions/{id}/messages` | — | 历史消息（含 citations） |
| DELETE | `/api/chat/sessions/{id}` | — | 删除会话 |
| POST | `/api/chat/sessions/{id}/messages` | `{content(≤2000,必填), topK?, minScore?}` | **SSE 流式问答**（`text/event-stream`） |
| POST | `/api/chat/sessions/{id}/cancel` | — | 取消生成 |

SSE 事件流逐块推送答案，终态持久化消息（content + citations JSON + status）。引用字段：`documentId/documentName/chunkId/pageNum/originalContent/similarityScore`。

## 八、学习计划与任务

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/api/study-plans` | `{title(必填), goal?, topic?, kbId?, startDate?, endDate?, dailyMinutes(5-1440)?, currentLevel?, expectedOutcome?}` | 创建计划 |
| GET | `/api/study-plans` | `page,size` | 计划列表 |
| GET | `/api/study-plans/{id}` | — | 详情 |
| PUT | `/api/study-plans/{id}` | `{title?, goal?, topic?, kbId?, startDate?, endDate?, dailyMinutes?, currentLevel?, expectedOutcome?, status?}` | 更新 |
| DELETE | `/api/study-plans/{id}` | — | 删除 |
| POST | `/api/study-plans/{id}/generate` | — | AI 生成逐日任务（异步） |
| GET | `/api/study-plans/{id}/generate-status` | — | 生成状态 |
| GET | `/api/study-plans/{id}/tasks` | — | 任务列表 |
| GET | `/api/study-plans/{id}/progress` | — | 进度 |
| PUT | `/api/study-tasks/{id}/status` | `{status(必填,0待办/1完成)}` | 打卡/取消完成（重算进度） |
| PUT | `/api/study-tasks/{id}` | `{title?, content?, focus?, recommendedContent?, stage?, taskDate?, durationMin?, stageQuiz?, sortOrder?}` | 编辑任务 |

## 九、智能出题与练习

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/api/quizzes/generate` | `{kbId(必填), topic?(≤128), title?(≤64), questionCount(1-20)?, difficulty(1-3)?, questionTypes?[1-4]}` | AI 出题（异步） |
| GET | `/api/quizzes` | `page,size` | 测验列表 |
| GET | `/api/quizzes/wrong-answers` | — | 错题回顾 |
| GET | `/api/quizzes/{id}` | — | 详情（练习态隐藏答案） |
| GET | `/api/quizzes/{id}/generate-status` | — | 生成状态 |
| POST | `/api/quizzes/{id}/submit` | `{answers:[{questionId, answer(≤500)}]}` | 提交判分（客观题自动+简答 AI） |
| GET | `/api/quizzes/{id}/result` | — | 成绩与解析（score 0-100） |
| DELETE | `/api/quizzes/{id}` | — | 删除 |

## 十、工作台统计（真实 MySQL 聚合）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/overview` | 9 项计数：知识库/论文文档/已解析/已向量化/问答会话/学习计划/已完成任务/练习次数/学习进度 |
| GET | `/api/dashboard/document-statistics` | 解析状态分布、向量化分布、7 天上传趋势 |
| GET | `/api/dashboard/learning-statistics` | 计划总数/任务总数/已完成/平均进度、计划进度列表、7 天活动趋势（4 系列） |
| GET | `/api/dashboard/quiz-statistics` | 测验总数/已练习/平均分、成绩分布（5 桶）、题型分布（4 类）、最近成绩、7 天练习趋势 |

无记录返回 0 / 诚实空状态，无模拟数据。

## 十一、真实调用示例（取自 M7 联调实测）

注册：
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"m7_demo","password":"test123456","nickname":"M7联调"}'
# {"code":200,"message":"success","data":{"token":"eyJ...","user":{...}}}
```
语义检索：
```bash
curl -X POST http://localhost:8080/api/knowledge-bases/1/search \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"query":"联邦学习通信优化","topK":5}'
# data: [{chunkId, documentId, content, pageNum, similarityScore:0.70...}, ...]
```
提交练习：
```bash
curl -X POST http://localhost:8080/api/quizzes/10/submit \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"answers":[{"questionId":1,"answer":"A"},{"questionId":2,"answer":"对"}]}'
# data: {score:67, ...}
```
健康检查：
```bash
curl http://localhost:8080/api/health
# {"code":200,"message":"success","data":{"application":"StudyPilot AI","status":"UP"}}
```

> 完整端到端链路（注册→登录→知识库→上传→解析→AI 分析→切片→Embedding→RAG→引用→计划→任务→出题→练习→成绩→工作台）的真实验证见 [TEST-REPORT.md](TEST-REPORT.md)。