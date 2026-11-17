-- ============================================================
-- StudyPilot AI M2 迁移脚本（在已初始化的 studypilot 库上执行）
-- 变更：biz_document 增加 need_ocr 字段（扫描版 PDF 显式 OCR 标记）
-- 执行: mysql -uroot -p studypilot < sql/migration-m2.sql
-- 注意：仅增量 ALTER，不重建表、不清空数据
-- ============================================================

USE `studypilot`;

ALTER TABLE `biz_document`
  ADD COLUMN `need_ocr` tinyint(1) NOT NULL DEFAULT 0 COMMENT '扫描版PDF需OCR：0-否 1-是' AFTER `char_count`;