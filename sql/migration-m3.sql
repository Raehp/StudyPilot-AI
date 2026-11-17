-- ============================================================
-- StudyPilot AI - M3 迁移脚本
-- 新建论文 AI 分析任务表（已获用户批准，2026-11-17）
-- 仅新增表，不修改/覆盖任何已有表与数据
-- 执行: mysql -uroot studypilot < sql/migration-m3.sql
-- ============================================================

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