# M2 科研知识库与 PDF 文档管理 — 交付报告

日期：2026-11-17
范围：知识库 CRUD、PDF 上传与安全校验、PDFBox 异步解析、文档状态管理、前端完整联调。
**本阶段未接入大模型、未调用 Embedding、未实现 RAG，未提前实现 M3/M4/M5 功能。认证模块零改动。**

---

## 1. 数据库变更

仅一处增量迁移（已获批准），未重建库、未覆盖已有数据：

```sql
-- sql/migration-m2.sql（已在 studypilot 库执行）
ALTER TABLE biz_document
  ADD COLUMN need_ocr tinyint(1) NOT NULL DEFAULT 0
  COMMENT '扫描版PDF需OCR：0-否 1-是' AFTER char_count;
```

- `sql/init.sql` 同步补充该字段定义，并将 `kb_id` 注释明确为「所属知识库（0=未归属文档池）」。
- 复用 M1 已建的 `biz_knowledge_base`、`biz_document` 两张表，未新建任何表。
- 迁移前后存量数据 0 行受影响（当时文档表为空），逻辑删除字段沿用 `deleted`。

## 2. 新增文件

### 后端（studypilot-backend/src/main/java/com/studypilot/）

| 分层 | 文件 | 职责 |
|---|---|---|
| Entity | `entity/KnowledgeBase.java`、`entity/Document.java` | 映射既有两张表，@TableLogic 逻辑删除 |
| Mapper | `mapper/KnowledgeBaseMapper.java`、`mapper/DocumentMapper.java` | MyBatis-Plus BaseMapper |
| DTO | `dto/KnowledgeBaseCreateDTO.java`、`dto/KnowledgeBaseUpdateDTO.java`、`dto/KbDocumentAddDTO.java` | 入参校验（@NotBlank/@NotNull、长度限制） |
| VO | `vo/KnowledgeBaseVO.java`、`vo/DocumentVO.java`、`vo/ParseStatusVO.java`、`vo/DocumentContentVO.java` | 出参；状态对外为字符串枚举；**不暴露 filePath** |
| Service | `service/KnowledgeBaseService.java` + `impl/KnowledgeBaseServiceImpl.java` | 知识库 CRUD、文档移入/移除、级联删除（事务） |
| Service | `service/DocumentService.java` + `impl/DocumentServiceImpl.java` | 上传、分页、详情、删除（事务 + 提交后清理文件） |
| Service | `service/DocumentStorageService.java` | 三重校验、UUID 安全落盘、路径穿越防护、解析结果 JSON 读写 |
| Service | `service/PdfParseTaskService.java` | 解析任务调度：Redis 锁 + 状态 CAS、状态查询、文本预览 |
| Service | `service/PdfParseService.java` | @Async 执行 PDFBox 逐页提取（跨 Bean 调用保证代理生效） |
| Controller | `controller/KnowledgeBaseController.java`、`controller/DocumentController.java` | 仅参数校验与 Result 包装，无业务逻辑、不直接操作 Mapper |
| Config | `config/StorageProperties.java` | `studypilot.storage.*` 配置绑定（存储目录不写死在业务代码） |
| Config | `config/AsyncConfig.java` | @EnableAsync + parseExecutor（core2/max4/queue100/AbortPolicy） |
| Common | `common/PageResult.java`、`common/DocumentStatus.java` | 统一分页结构；DB tinyint ↔ 对外状态映射 |

### 前端（studypilot-frontend/src/）

| 文件 | 职责 |
|---|---|
| `api/knowledge.ts` | 知识库 CRUD + 移入/移除文档接口 |
| `api/document.ts` | 上传（FormData+进度）、分页、详情、删除、解析状态、触发解析、文本预览 |
| `views/knowledge/KnowledgeView.vue` | 重写为完整功能页（见 §6） |

### 其他

- `sql/migration-m2.sql` — 迁移脚本。
- 测试资产位于仓库外 `E:/AI-Qoder-Pro/test-assets/`（生成器 + 8 种测试 PDF + 验收脚本 `m2-final-test.mjs`），不入库。

## 3. 修改文件

| 文件 | 变更 |
|---|---|
| `studypilot-backend/pom.xml` | 新增 `org.apache.pdfbox:pdfbox:3.0.3` |
| `studypilot-backend/src/main/resources/application.yml` | multipart 上限 30MB/35MB；新增 `studypilot.storage.upload-dir=${STORAGE_DIR:./data/uploads}`、`max-size-mb: 30`。**密钥仍为环境变量占位，已复查无硬编码** |
| `sql/init.sql` | need_ocr 字段 + kb_id 注释 |
| `.gitignore` | 新增 `data/`（上传文件不入库） |
| `studypilot-frontend/src/types/api.ts` | PageResult、KnowledgeBaseVO/DTO、DocumentVO、DocumentStatus、ParseStatusVO、DocumentContentVO 等类型 |
| `studypilot-frontend/src/utils/request.ts` | 新增 `http.upload`（120s 超时 + onUploadProgress） |
| `studypilot-frontend/src/views/knowledge/KnowledgeView.vue` | 完整实现 + 事件处理器错误捕获修复 |

**未改动**：security 包、JwtInterceptor、UserServiceImpl、GlobalExceptionHandler、Result/ResultCode（复用既有 400/401/403/404/429 语义；MaxUploadSizeExceededException 已有处理）。

## 4. API 清单（全部经 JwtInterceptor 鉴权，Result 统一包装）

### 知识库

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/knowledge-bases` | 创建（绑定当前用户） |
| GET | `/api/knowledge-bases` | 当前用户列表（createTime 倒序） |
| GET | `/api/knowledge-bases/{id}` | 详情（非本人 → 404） |
| PUT | `/api/knowledge-bases/{id}` | 更新名称/描述 |
| DELETE | `/api/knowledge-bases/{id}` | 删除（级联，见 §5） |
| POST | `/api/knowledge-bases/{id}/documents` | 移入文档（body: documentId；从原库/池移动，双库计数事务调整） |
| DELETE | `/api/knowledge-bases/{id}/documents/{documentId}` | 移出文档 → 未归属文档池（kb_id=0），不删文件 |

### 文档

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/documents/upload` | multipart：file + 可选 kbId（缺省入池）；三重校验 |
| GET | `/api/documents?kbId=&page=&size=` | 分页；kbId=0 查未归属池；缺省查本人全部 |
| GET | `/api/documents/{id}` | 详情 |
| DELETE | `/api/documents/{id}` | 逻辑删除 + 提交后物理清理；解析中禁止删除（400） |
| GET | `/api/documents/{id}/parse-status` | 状态/页数/字符数/needOcr/脱敏错误信息 |
| POST | `/api/documents/{id}/parse` | 手动触发/重试解析（并发防护见 §5） |
| GET | `/api/documents/{id}/content` | **规格外补充端点**：逐页解析文本预览（前端预览 dialog 需要；仅 SUCCESS 可读） |

> 库内文档分页按规格 API 清单由 `GET /api/documents?kbId={id}` 承担，未另设 `GET /knowledge-bases/{id}/documents`。
> 任务调度类命名为 `PdfParseTaskService`（规格建议名），实际执行类为 `PdfParseService`。

## 5. 关键设计

### 状态映射（DB tinyint → 对外字符串）

| DB | 枚举 | 对外 |
|---|---|---|
| 0 | UPLOADED | PENDING |
| 1 | PARSING | PROCESSING |
| 2/3 | CHUNKING/EMBEDDING | （M4 预留，本阶段不产生） |
| 4 | READY | SUCCESS |
| 5 | FAILED | FAILED |

### 上传安全

- 三重校验：`.pdf` 扩展名 + `application/pdf` MIME + 前 1024 字节内 `%PDF-` 魔数；伪装文件（fake.pdf）被拒。
- 大小：Spring multipart 30MB 硬限 + 业务层 `max-size-mb` 配置校验，超限统一 400。
- 落盘：服务端生成 `pdf/{UUID无横线}.pdf`，绝不使用用户文件名；存储根目录来自配置项。
- 读取/删除均经 `normalize + startsWith(root)` 路径穿越防护。
- 元数据入 MySQL（user_id/kb_id/大小/相对路径/状态/时间）；上传失败回滚时同步删除已落盘文件。

### 异步解析与并发防护

- 上传事务 `afterCommit` 后投递解析，避免 worker 读不到未提交数据。
- Redis SETNX 锁 `studypilot:parse:lock:{docId}`（TTL 10min）+ 数据库状态 CAS 双重防护；线程池拒绝（AbortPolicy）时回滚状态与锁并返回 429。
- 已成功再解析 → 400「文档已解析成功，无需重复解析」；解析中重复提交 → 429。
- PDFBox 3.0.3 逐页提取（setStartPage/setEndPage），保留页码；结果存 `{storage}/parsed/{docId}.json`（已批准的文件存储方案，不加 DB 列）。
- 加密 PDF（InvalidPasswordException）/损坏/0 页 → FAILED，前端仅见固定业务文案，完整堆栈只进服务端日志。
- **扫描版（提取字符数为 0）→ SUCCESS + needOcr=1，前端显示「扫描版·需OCR」，不伪造识别结果。**

### 删除与隔离

- 知识库删除（已批准级联方案）：事务内逻辑删除库 + 库内本人文档，`afterCommit` 物理删除 PDF 与解析 JSON；前端删除确认明确提示「将同时删除其中 N 篇文档及其文件」。
- 所有读写以 `user_id` 过滤；跨用户访问统一 404（不泄露存在性）；移除文档仅改 kb_id，不触碰他人资源。
- 文档计数 `doc_count` 用 `GREATEST(doc_count-1,0)` 防负数。

## 6. 前端实现（全部真实接口数据，无静态模拟）

- 左侧知识库列表（搜索过滤、悬停编辑/删除、未归属文档池 + 实时计数）；右侧文档面板。
- 创建/编辑知识库 dialog（表单校验）；删除二次确认（级联警告）。
- el-upload 拖拽上传：前端扩展名/大小预检 + 自定义 http-request 进度环 + 上传成功自动入解析队列。
- 文档表格：大小/页数/字符数/解析状态（PENDING/PROCESSING 带 Loading，SUCCESS/FAILED 标签，OCR 标记，失败原因行内展示）/上传时间/操作。
- PENDING/PROCESSING 行 3s 轮询 parse-status，全部落定后自动刷新库计数；组件卸载停止轮询。
- 预览 dialog：元信息 + needOcr 诚实提示 + 逐页文本；失败文档「重试解析」；池内文档「移入知识库」dialog；库内文档「移除」回池。
- 空状态使用 EmptyState 组件，无任何伪造数据。
- 修复：确认框「取消」与请求异常的 Promise 拒绝原先未捕获（控制台 2 条警告），已为全部事件处理器补静默捕获，复测控制台零错误。

## 7. 测试结果（真实数据库 + 真实文件，2026-11-17 执行）

验收脚本：`E:/AI-Qoder-Pro/test-assets/m2-final-test.mjs`（node 运行，demo/lisi 双账号）。
最终一轮 **15/15 PASS**（T1–T14 规格项 + T8b 加密附加项）：

| 编号 | 项目 | 结果 | 证据摘要 |
|---|---|---|---|
| T1 | 创建知识库 | PASS | code=200，id=7 |
| T2 | 查询知识库 | PASS | 列表含新库；详情名称正确；库内文档分页 total=0 |
| T3 | 更新知识库 | PASS | 改名「M2验收库-改」回读一致 |
| T4 | 删除知识库（级联） | PASS | DELETE=200；库 GET=404；库内 4 文档 GET 均 404；DB deleted=1；**4 个 PDF + 2 个解析 JSON 磁盘核查均已物理删除** |
| T5 | 上传真实 PDF | PASS | docId=14，PENDING，UUID 落盘，元数据入库 |
| T6 | 非 PDF/伪装拒绝 | PASS | normal.txt→400「扩展名不符」；fake.pdf→400「文件头校验失败」 |
| T7 | 超 30MB 拒绝 | PASS | 31MB oversize.pdf→400「上传文件超出大小限制」 |
| T8 | 损坏 PDF | PASS | 解析 FAILED，errorMsg=「PDF 文件损坏或格式异常，无法解析」（无路径/堆栈） |
| T8b | 加密 PDF | PASS | FAILED，errorMsg=「文档已加密，无法提取文本」 |
| T9 | 正常解析 | PASS | SUCCESS，2 页/176 字符；content 返回 2 个 PageText，页码正确，文本与源 PDF 一致 |
| T10 | 扫描版/空白 | PASS | SUCCESS + needOcr=true + charCount=0（诚实标记） |
| T11 | 未认证访问 | PASS | 无 token GET 知识库/文档均 401 |
| T12 | 跨用户知识库 | PASS | lisi 对 demo 库 GET/PUT/DELETE 均 404 |
| T13 | 跨用户文档 | PASS | lisi 对 demo 文档 GET/DELETE/parse-status/content 均 404 |
| T14 | 重复/并发解析 | PASS | 已成功再解析→400；300 页 big.pdf 解析中并发 4 请求→**[429,429,429,429]**；最终 SUCCESS 300 页/940,080 字符 |

补充验证（同轮/前轮，均有 DB 或磁盘证据）：

- 移入/移除文档：kb_id 变更 + 双库 doc_count 正确（UI 往返测试：移除→池 1/库 0，移入→池 0/库 1）。
- 文档删除：解析中删除被拒（400）；正常删除后 deleted=1、doc_count-1、PDF+JSON 物理删除（doc8 磁盘核查）。
- FAILED 重试：encrypted 文档 UI 重试 → PROCESSING → FAILED，文案脱敏一致。
- 池文档不受级联影响：doc18（big.pdf）留在池中，PDF 与 parsed/18.json 保留。

### 浏览器端到端（localhost:5173，真实后端）

登录→知识库列表/文档表格真实数据→创建/编辑/删除库 dialog→拖拽上传（DataTransfer 注入真实 PDF）→自动解析+轮询+计数刷新→逐页预览→OCR 标记与失败原因展示→移除/移入 dialog→删除确认→重试解析，全部通过；`list_console_messages` 最终仅 vite 连接 debug，**零错误零警告**。

### 编译与构建

- 后端：`mvn clean compile` → **BUILD SUCCESS**（2026-11-17 19:32）。
- 前端：`vue-tsc --noEmit` → **0 错误**；`npm run build` → **构建成功**（10.2s，仅 chunk 体积提示）。

## 8. 已知问题与权衡

1. **单会话语义**：curl/脚本登录会使浏览器既有 token 失效（M1 既定设计），联调时需重新登录；非缺陷。
2. **扫描版 PDF 仅标记需 OCR**，本阶段不做 OCR（规格要求诚实标记）；预览区显示明确提示。
3. **解析锁 TTL 10min**：极端情况下进程崩溃会留下 PARSING 状态，但无锁时允许重新触发解析（已实现 stale 恢复路径）；未做开机扫描修复。
4. **doc_count 为冗余计数**：并发极端场景依赖事务与 GREATEST 保护，未做定期对账。
5. 大文件（300 页/940k 字符）解析结果 JSON 约 1MB 级，预览接口整包返回；M4 分块后可改为分段读取。
6. 前端构建有 chunk >500kB 体积提示（Element Plus 整包），不影响功能，后续可做手动分包。
7. 测试产生的真实数据保留在库中（kb1 深度学习论文库 v2 含 6 篇各状态文档、kb5 UI联调测试库v2 空库、池内 big.pdf），可作为演示数据；验收临时库已级联删除。

## 9. 未完成功能（按规格属后续里程碑）

- OCR 识别（扫描版仅标记）。
- 文本分块 CHUNKING / 向量化 EMBEDDING（M4）。
- 大模型摘要/分析、RAG 问答（M3/M5）。
- 知识库分享/协作、文档在线编辑（不在 M2 范围）。

## 10. 运行方式

```bash
# 后端（存储目录默认 studypilot-backend/data/uploads，可用 STORAGE_DIR 覆盖）
MYSQL_PASSWORD=*** mvn -f studypilot-backend/pom.xml spring-boot:run
# 前端
cd studypilot-frontend && npm run dev
# 验收脚本（仓库外）
node E:/AI-Qoder-Pro/test-assets/m2-final-test.mjs
```

---

**M2 开发完毕，暂停等待确认，不自动进入 M3。**