-- =============================================================
-- StudyPilot AI - M4 迁移：文档切片与 Embedding
-- 执行: mysql -uroot -p --default-character-set=utf8mb4 studypilot < sql/migration-m4.sql
-- 注意: Windows mysql 客户端必须带 --default-character-set=utf8mb4，否则中文注释乱码
-- 不触碰已有数据：biz_document_chunk 为 M1 预留空表，仅追加字段；biz_document 追加字段
-- =============================================================

-- M1 预留 biz_document_chunk 补齐 M4 规格字段（保留原 doc_id/page_num/embedding 等命名）
ALTER TABLE `biz_document_chunk`
  ADD COLUMN `user_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '所属用户ID' AFTER `id`,
  ADD COLUMN `chunk_uid` varchar(64) NOT NULL DEFAULT '' COMMENT '稳定唯一标识 SHA-256(documentId:chunkIndex:contentHash)' AFTER `user_id`,
  ADD COLUMN `source_position` varchar(64) NOT NULL DEFAULT '' COMMENT '原文位置 p{页}:{起}-{止}（清洗后全文字符偏移）' AFTER `page_num`,
  ADD COLUMN `embedding_model` varchar(64) NOT NULL DEFAULT '' COMMENT 'Embedding 模型名称' AFTER `embedding`,
  ADD COLUMN `embedding_dimension` int NOT NULL DEFAULT 0 COMMENT '向量维度' AFTER `embedding_model`,
  ADD COLUMN `embedding_version` varchar(32) NOT NULL DEFAULT '' COMMENT '模型版本标识' AFTER `embedding_dimension`,
  ADD KEY `idx_user_kb` (`user_id`, `kb_id`);

-- biz_document 追加向量化任务字段
ALTER TABLE `biz_document`
  ADD COLUMN `vector_status` tinyint NOT NULL DEFAULT 0 COMMENT '向量化状态：0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED' AFTER `analyzed`,
  ADD COLUMN `chunk_count` int NOT NULL DEFAULT 0 COMMENT '切片数量' AFTER `vector_status`,
  ADD COLUMN `embedding_count` int NOT NULL DEFAULT 0 COMMENT '已嵌入向量数量' AFTER `chunk_count`,
  ADD COLUMN `vector_error_msg` varchar(500) NOT NULL DEFAULT '' COMMENT '向量化错误信息' AFTER `embedding_count`;