-- ============================================================
-- StudyPilot AI 初始化建表脚本
-- MySQL 8.0 / utf8mb4 / InnoDB / 无物理外键（应用层保证关联）
-- 执行: mysql -uroot -p --default-character-set=utf8mb4 studypilot < sql/init.sql
-- 注意: Windows 下必须带 --default-character-set=utf8mb4，否则中文注释会按 GBK 误读损坏
-- ============================================================

CREATE DATABASE IF NOT EXISTS `studypilot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `studypilot`;

-- ① 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(32) NOT NULL COMMENT '登录名',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt哈希',
  `nickname` varchar(32) NOT NULL DEFAULT '' COMMENT '显示名',
  `email` varchar(64) NOT NULL DEFAULT '' COMMENT '邮箱',
  `avatar` varchar(255) NOT NULL DEFAULT '' COMMENT '头像URL',
  `major` varchar(64) NOT NULL DEFAULT '' COMMENT '专业',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ② 知识库表
CREATE TABLE IF NOT EXISTS `biz_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '描述',
  `doc_count` int NOT NULL DEFAULT 0 COMMENT '文档数（冗余）',
  `chunk_count` int NOT NULL DEFAULT 0 COMMENT '切片数（冗余）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ③ 文档表
CREATE TABLE IF NOT EXISTS `biz_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_id` bigint unsigned NOT NULL COMMENT '所属知识库（0=未归属文档池）',
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户（权限校验）',
  `title` varchar(128) NOT NULL DEFAULT '' COMMENT '论文标题',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(255) NOT NULL COMMENT '存储相对路径',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '文件字节数',
  `page_count` int NOT NULL DEFAULT 0 COMMENT 'PDF页数',
  `char_count` int NOT NULL DEFAULT 0 COMMENT '提取字符数',
  `need_ocr` tinyint(1) NOT NULL DEFAULT 0 COMMENT '扫描版PDF需OCR：0-否 1-是',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-UPLOADED 1-PARSING 2-CHUNKING 3-EMBEDDING 4-READY 5-FAILED',
  `error_msg` varchar(500) NOT NULL DEFAULT '' COMMENT '失败原因',
  `summary` text COMMENT 'AI摘要',
  `analysis` json DEFAULT NULL COMMENT '结构化分析：研究问题/方法/创新点/结论/局限性',
  `analyzed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已完成AI分析',
  `vector_status` tinyint NOT NULL DEFAULT 0 COMMENT '向量化状态：0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED',
  `chunk_count` int NOT NULL DEFAULT 0 COMMENT '切片数量',
  `embedding_count` int NOT NULL DEFAULT 0 COMMENT '已嵌入向量数量',
  `vector_error_msg` varchar(500) NOT NULL DEFAULT '' COMMENT '向量化错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- ④ 文档切片与向量表（向量检索核心表；派生数据，重新处理/删除文档时物理清理重建）
CREATE TABLE IF NOT EXISTS `biz_document_chunk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `chunk_uid` varchar(64) NOT NULL DEFAULT '' COMMENT '稳定唯一标识 SHA-256(documentId:chunkIndex:contentHash)',
  `doc_id` bigint unsigned NOT NULL COMMENT '所属文档',
  `kb_id` bigint unsigned NOT NULL COMMENT '所属知识库（冗余，检索免join）',
  `chunk_index` int NOT NULL DEFAULT 0 COMMENT '切片序号',
  `page_num` int NOT NULL DEFAULT 0 COMMENT '原文页码',
  `source_position` varchar(64) NOT NULL DEFAULT '' COMMENT '原文位置 p{页}:{起}-{止}（清洗后全文字符偏移）',
  `content` text NOT NULL COMMENT '切片原文',
  `char_count` int NOT NULL DEFAULT 0 COMMENT '字符数',
  `embedding` json DEFAULT NULL COMMENT '向量JSON数组，NULL=未向量化',
  `embedding_model` varchar(64) NOT NULL DEFAULT '' COMMENT 'Embedding 模型名称',
  `embedding_dimension` int NOT NULL DEFAULT 0 COMMENT '向量维度',
  `embedding_version` varchar(32) NOT NULL DEFAULT '' COMMENT '模型版本标识',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_user_kb` (`user_id`, `kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切片与向量表';

-- ⑤ 会话表
CREATE TABLE IF NOT EXISTS `biz_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户',
  `kb_id` bigint unsigned NOT NULL COMMENT '问答范围知识库',
  `title` varchar(64) NOT NULL DEFAULT '' COMMENT '会话标题',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG会话表';

-- ⑥ 聊天消息表
CREATE TABLE IF NOT EXISTS `biz_chat_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint unsigned NOT NULL COMMENT '所属会话',
  `role` tinyint NOT NULL COMMENT '0-user 1-assistant',
  `content` mediumtext NOT NULL COMMENT '消息内容',
  `citations` json DEFAULT NULL COMMENT '引用数组：chunkId/docId/docTitle/pageNum/score/snippet',
  `token_count` int NOT NULL DEFAULT 0 COMMENT '消耗token',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ⑦ 学习计划表
CREATE TABLE IF NOT EXISTS `biz_study_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户',
  `title` varchar(64) NOT NULL COMMENT '计划名',
  `goal` varchar(500) NOT NULL DEFAULT '' COMMENT '学习目标',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-进行中 1-已完成 2-已放弃',
  `progress` int NOT NULL DEFAULT 0 COMMENT '完成百分比（冗余）',
  `ai_advice` text COMMENT 'AI总体建议',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习计划表';

-- ⑧ 计划任务表
CREATE TABLE IF NOT EXISTS `biz_study_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` bigint unsigned NOT NULL COMMENT '所属计划',
  `task_date` date DEFAULT NULL COMMENT '计划日期',
  `title` varchar(128) NOT NULL COMMENT '任务名',
  `content` varchar(1000) NOT NULL DEFAULT '' COMMENT '任务详情',
  `duration_min` int NOT NULL DEFAULT 0 COMMENT '预计时长（分钟）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待办 1-已完成',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习计划任务表';

-- ⑨ 测验表
CREATE TABLE IF NOT EXISTS `biz_quiz` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户',
  `doc_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '出题来源文档（0=按知识库）',
  `kb_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '来源知识库',
  `title` varchar(64) NOT NULL COMMENT '测验名',
  `difficulty` tinyint NOT NULL DEFAULT 1 COMMENT '1-基础 2-进阶 3-挑战',
  `question_count` int NOT NULL DEFAULT 0 COMMENT '题数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-未作答 1-已提交',
  `score` int NOT NULL DEFAULT 0 COMMENT '得分',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能测验表';

-- ⑩ 测验题目表
CREATE TABLE IF NOT EXISTS `biz_quiz_question` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `quiz_id` bigint unsigned NOT NULL COMMENT '所属测验',
  `type` tinyint NOT NULL COMMENT '1-单选 2-多选 3-判断 4-简答',
  `stem` text NOT NULL COMMENT '题干',
  `options` json DEFAULT NULL COMMENT '选项数组',
  `answer` varchar(500) NOT NULL DEFAULT '' COMMENT '正确答案',
  `analysis` text COMMENT 'AI解析',
  `source_chunk_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '出题依据切片（溯源）',
  `user_answer` varchar(500) NOT NULL DEFAULT '' COMMENT '用户作答',
  `is_correct` tinyint NOT NULL DEFAULT -1 COMMENT '-1-未判分 0-错误 1-正确',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '题号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_quiz_id` (`quiz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验题目表';

-- ⑪ AI调用日志表
CREATE TABLE IF NOT EXISTS `log_ai_call` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '调用用户',
  `biz_type` tinyint NOT NULL COMMENT '1-摘要 2-分析 3-RAG 4-计划 5-出题 6-判分',
  `model` varchar(32) NOT NULL DEFAULT '' COMMENT '模型名',
  `prompt_tokens` int NOT NULL DEFAULT 0 COMMENT '输入token',
  `completion_tokens` int NOT NULL DEFAULT 0 COMMENT '输出token',
  `latency_ms` int NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-成功 1-失败 2-超时 3-限流',
  `error_msg` varchar(500) NOT NULL DEFAULT '' COMMENT '异常信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_create_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日志表';

-- ⑫ 论文AI分析任务表（M3）
CREATE TABLE IF NOT EXISTS `biz_paper_analysis` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint unsigned NOT NULL COMMENT '文档ID（biz_document.id）',
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户（权限校验）',
  `model` varchar(64) NOT NULL DEFAULT '' COMMENT '模型名称（如 qwen-plus）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '任务状态：0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED',
  `error_msg` varchar(500) NOT NULL DEFAULT '' COMMENT '失败原因（脱敏，不含路径/堆栈/密钥）',
  `result` json DEFAULT NULL COMMENT '12字段结构化分析结果JSON',
  `summary` text COMMENT '论文摘要（冗余，便于列表展示）',
  `duration_ms` bigint NOT NULL DEFAULT 0 COMMENT '分析总耗时（毫秒）',
  `segment_count` int NOT NULL DEFAULT 0 COMMENT '分段分析段数（0=单段直析）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='论文AI分析任务表';