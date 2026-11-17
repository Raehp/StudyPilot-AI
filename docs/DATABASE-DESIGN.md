# StudyPilot AI 数据库设计（DATABASE-DESIGN）

> 来源：`sql/init.sql`（M1–M6 合并后的完整 schema，与实际运行库一致）。MySQL 8.0 / InnoDB / utf8mb4_unicode_ci。
> 全新部署仅执行 `init.sql`；`migration-m2..m6.sql` 为开发期历史增量，已并入 init.sql，勿重复执行。

## 一、设计约定

- 主键：`id bigint unsigned AUTO_INCREMENT`。
- 三件套：`create_time` / `update_time`（ON UPDATE CURRENT_TIMESTAMP）/ `deleted`（逻辑删除 0-未删 1-已删，MyBatis-Plus `@TableLogic` 自动过滤）。
- 字符集：utf8mb4 / utf8mb4_unicode_ci（支持 emoji 与中文）。
- **无物理外键**：关联与级联清理由应用层保证（删除文档/知识库时清理切片、物理文件、解析结果）。
- 状态/枚举：`tinyint` + 注释，不用 MySQL enum。
- 冗余字段：`kb_id`/`user_id` 在切片表冗余以免 join；`doc_count`/`chunk_count`/`progress` 冗余计数便于列表与统计。
- 向量与引用：MySQL `json` 字段（embedding 数组、citations 引用数组、analysis 结构化结果）。

## 二、表清单（12 张）

| # | 表 | 说明 | 关键索引 |
|---|----|------|----------|
| 1 | `sys_user` | 用户 | uk_username |
| 2 | `biz_knowledge_base` | 知识库 | idx_user_id |
| 3 | `biz_document` | 文档（PDF） | idx_kb_id, idx_user_id |
| 4 | `biz_document_chunk` | 文档切片与向量 | idx_doc_id, idx_kb_id, **idx_user_kb(user_id,kb_id)** |
| 5 | `biz_conversation` | RAG 会话 | idx_user_id |
| 6 | `biz_chat_message` | 聊天消息（citations JSON） | idx_conversation_id |
| 7 | `biz_study_plan` | 学习计划 | idx_user_id |
| 8 | `biz_study_task` | 计划任务 | idx_plan_id |
| 9 | `biz_quiz` | 智能测验 | idx_user_id |
| 10 | `biz_quiz_question` | 测验题目 | idx_quiz_id |
| 11 | `log_ai_call` | AI 调用日志 | idx_user_id_create_time(user_id,create_time) |
| 12 | `biz_paper_analysis` | 论文 AI 分析任务 | idx_document_id, idx_user_id |

## 三、表结构详解

### 1. sys_user 用户表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint unsigned PK | 主键 |
| username | varchar(32) NOT NULL | 登录名，唯一 |
| password | varchar(100) NOT NULL | BCrypt 哈希 |
| nickname | varchar(32) | 显示名 |
| email | varchar(64) | 邮箱 |
| avatar | varchar(255) | 头像 URL |
| major | varchar(64) | 专业 |
| status | tinyint | 0-禁用 1-正常 |
| create_time/update_time/deleted | | 三件套 |
索引：`UNIQUE uk_username(username)`。

### 2. biz_knowledge_base 知识库表
user_id（归属）、name varchar(64)、description varchar(255)、doc_count int（冗余文档数）、chunk_count int（冗余切片数）+ 三件套。索引 `idx_user_id`。

### 3. biz_document 文档表
| 字段 | 类型 | 说明 |
|------|------|------|
| kb_id | bigint unsigned | 所属知识库（0=未归属文档池） |
| user_id | bigint unsigned | 归属用户（权限校验） |
| title | varchar(128) | 论文标题 |
| file_name | varchar(255) | 原始文件名 |
| file_path | varchar(255) | 存储相对路径（UUID 重命名，**不对外暴露**） |
| file_size | bigint | 字节数 |
| page_count | int | PDF 页数 |
| char_count | int | 提取字符数 |
| need_ocr | tinyint(1) | 扫描版需 OCR：0-否 1-是 |
| status | tinyint | 0-UPLOADED 1-PARSING 2-CHUNKING 3-EMBEDDING 4-READY 5-FAILED |
| error_msg | varchar(500) | 失败原因（脱敏） |
| summary | text | AI 摘要 |
| analysis | json | 结构化分析 |
| analyzed | tinyint(1) | 是否完成 AI 分析 |
| vector_status | tinyint | 0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED |
| chunk_count | int | 切片数 |
| embedding_count | int | 已嵌入向量数 |
| vector_error_msg | varchar(500) | 向量化错误 |
索引：`idx_kb_id`、`idx_user_id`。

### 4. biz_document_chunk 文档切片与向量表（检索核心）
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint unsigned | 所属用户（检索隔离） |
| chunk_uid | varchar(64) | SHA-256(documentId:chunkIndex:contentHash) |
| doc_id | bigint unsigned | 所属文档（**列名 doc_id**） |
| kb_id | bigint unsigned | 所属知识库（冗余，检索免 join） |
| chunk_index | int | 切片序号 |
| page_num | int | 原文页码 |
| source_position | varchar(64) | 原文位置 p{页}:{起}-{止} |
| content | text | 切片原文 |
| char_count | int | 字符数 |
| embedding | json | 向量数组（1024 维），NULL=未向量化 |
| embedding_model | varchar(64) | 模型名 |
| embedding_dimension | int | 维度 |
| embedding_version | varchar(32) | 版本标识 |
索引：`idx_doc_id`、`idx_kb_id`、`idx_user_kb(user_id,kb_id)`（检索预过滤）。派生数据，删除/重处理文档时**物理清理重建**。

### 5. biz_conversation RAG 会话表
user_id、kb_id（问答范围）、title varchar(64) + 三件套。索引 `idx_user_id`。

### 6. biz_chat_message 聊天消息表
| 字段 | 类型 | 说明 |
|------|------|------|
| conversation_id | bigint unsigned | 所属会话 |
| role | tinyint | 0-user 1-assistant |
| content | mediumtext | 消息内容 |
| citations | json | 引用数组：documentId/documentName/chunkId/pageNum/originalContent/similarityScore |
| token_count | int | 消耗 token |
| status | tinyint | 0-生成中 1-成功 2-失败 3-已停止 |
| error_msg | varchar(255) | 失败/停止脱敏提示 |
索引：`idx_conversation_id`。

### 7. biz_study_plan 学习计划表
user_id、kb_id（0=不结合文档）、title varchar(64)、goal varchar(500)、topic varchar(128)、start_date/end_date date、daily_minutes int、current_level varchar(32)、expected_outcome varchar(500)、status tinyint（0-进行中 1-已完成 2-已放弃）、generate_status tinyint（0-未生成 1-生成中 2-成功 3-失败）、generate_error varchar(255)、progress int（完成百分比冗余）、ai_advice text。索引 `idx_user_id`。

### 8. biz_study_task 计划任务表
plan_id、user_id（冗余鉴权）、stage varchar(64)（学习阶段）、task_date date、title varchar(128)、content varchar(1000)、focus varchar(500)、recommended_content varchar(1000)、duration_min int、stage_quiz tinyint（0-否 1-是）、status tinyint（0-待办 1-已完成）、sort_order int。索引 `idx_plan_id`。

### 9. biz_quiz 智能测验表
user_id、doc_id（0=按知识库）、kb_id、topic varchar(128)、title varchar(64)、difficulty tinyint（1-基础 2-进阶 3-挑战）、question_types varchar(32)（逗号分隔 1单选 2多选 3判断 4简答）、question_count int、status tinyint（0-未作答 1-已提交）、generate_status tinyint、generate_error varchar(255)、score int（0-100 百分比）。索引 `idx_user_id`。

### 10. biz_quiz_question 测验题目表
| 字段 | 类型 | 说明 |
|------|------|------|
| quiz_id | bigint unsigned | 所属测验 |
| type | tinyint | 1-单选 2-多选 3-判断 4-简答 |
| difficulty | tinyint | 1-基础 2-进阶 3-挑战 |
| stem | text | 题干 |
| options | json | 选项数组 |
| answer | varchar(500) | 正确答案（练习态隐藏） |
| analysis | text | AI 解析 |
| knowledge_point | varchar(128) | 知识点 |
| source_chunk_id | bigint unsigned | 出题依据切片（溯源） |
| source_doc_id | bigint unsigned | 来源文档 |
| source_content | text | 来源原文片段 |
| user_answer | varchar(500) | 用户作答 |
| is_correct | tinyint | -1-未判分 0-错误 1-正确 |
| score | int | 本题得分（0-100） |
| score_basis | text | 简答 AI 评分依据 |
| sort_order | int | 题号 |
索引：`idx_quiz_id`。

### 11. log_ai_call AI 调用日志表
user_id、biz_type tinyint（1-摘要 2-分析 3-RAG 4-计划 5-出题 6-判分）、model varchar(32)、prompt_tokens int、completion_tokens int、latency_ms int、status tinyint（0-成功 1-失败 2-超时 3-限流）、error_msg varchar(500)。索引 `idx_user_id_create_time(user_id,create_time)`。**不记录密钥/敏感原文**。

### 12. biz_paper_analysis 论文 AI 分析任务表
document_id、user_id、model varchar(64)、status tinyint（0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED）、error_msg varchar(500)（脱敏）、result json（12 字段结构化分析）、summary text（冗余）、duration_ms bigint、segment_count int（0=单段直析）。索引 `idx_document_id`、`idx_user_id`。

## 四、实体关系（应用层关联，无物理外键）

```
sys_user 1───* biz_knowledge_base 1───* biz_document 1───* biz_document_chunk
   │                  │                      │ 1───1 biz_paper_analysis
   │                  │                      │
   │                  └──* biz_conversation 1───* biz_chat_message
   │                  └──* biz_quiz 1───* biz_quiz_question
   └──* biz_study_plan 1───* biz_study_task
   └──* log_ai_call
```
- 切片冗余 user_id/kb_id：检索按 `idx_user_kb` 预过滤，免 join 且强制隔离。
- 测验题目冗余 source_doc_id/source_chunk_id/source_content：引用溯源。
- 级联清理：删除文档→切片物理删除 + PDF/解析 JSON 删除；删除知识库→其文档级联逻辑删除 + 物理清理。

## 五、索引与性能说明

- 检索路径：`biz_document_chunk` 按 `(user_id,kb_id)` 走 `idx_user_kb` 取候选，再 JVM 余弦排序。
- 分页：MyBatis-Plus PaginationInnerInterceptor maxLimit=500，防超大分页（实测 size=1000→500）。
- 统计：工作台按 user_id 聚合，overview 实测平均约 12ms。
- 待批准优化（M7 未擅自实施）：`biz_document(user_id,status)`、`biz_quiz(user_id,create_time)` 等复合索引；切片量 >1 万评估 ANN。详见 M7-performance-report.md。