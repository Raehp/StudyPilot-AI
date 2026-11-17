-- M6 迁移：AI 学习计划与智能出题（已获用户批准 2026-11-17）
-- 仅对 M1 已建的 4 张表 ADD COLUMN，不新建表、不重建数据库、不改动已有列与数据。

-- ① 学习计划表：补充目标字段与 AI 生成状态
ALTER TABLE `biz_study_plan`
  ADD COLUMN `kb_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '关联知识库（0=不结合文档）' AFTER `user_id`,
  ADD COLUMN `topic` varchar(128) NOT NULL DEFAULT '' COMMENT '学习主题' AFTER `goal`,
  ADD COLUMN `daily_minutes` int NOT NULL DEFAULT 60 COMMENT '每天学习时长（分钟）' AFTER `end_date`,
  ADD COLUMN `current_level` varchar(32) NOT NULL DEFAULT '' COMMENT '当前知识水平（入门/进阶/熟练）' AFTER `daily_minutes`,
  ADD COLUMN `expected_outcome` varchar(500) NOT NULL DEFAULT '' COMMENT '预期学习成果' AFTER `current_level`,
  ADD COLUMN `generate_status` tinyint NOT NULL DEFAULT 0 COMMENT 'AI生成状态：0-未生成 1-生成中 2-成功 3-失败' AFTER `status`,
  ADD COLUMN `generate_error` varchar(255) NOT NULL DEFAULT '' COMMENT '生成失败脱敏提示' AFTER `generate_status`;

-- ② 计划任务表：补充阶段/重点/推荐内容/阶段测验与归属用户
ALTER TABLE `biz_study_task`
  ADD COLUMN `user_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '归属用户（冗余，便于直接鉴权）' AFTER `plan_id`,
  ADD COLUMN `stage` varchar(64) NOT NULL DEFAULT '' COMMENT '学习阶段' AFTER `user_id`,
  ADD COLUMN `focus` varchar(500) NOT NULL DEFAULT '' COMMENT '学习重点' AFTER `content`,
  ADD COLUMN `recommended_content` varchar(1000) NOT NULL DEFAULT '' COMMENT '推荐学习内容' AFTER `focus`,
  ADD COLUMN `stage_quiz` tinyint NOT NULL DEFAULT 0 COMMENT '是否阶段性测验：0-否 1-是' AFTER `duration_min`;

-- ③ 测验表：补充主题/题型集合与 AI 生成状态（score 定义为 0-100 百分比）
ALTER TABLE `biz_quiz`
  ADD COLUMN `topic` varchar(128) NOT NULL DEFAULT '' COMMENT '知识主题' AFTER `kb_id`,
  ADD COLUMN `question_types` varchar(32) NOT NULL DEFAULT '' COMMENT '题型集合（逗号分隔：1单选 2多选 3判断 4简答）' AFTER `difficulty`,
  ADD COLUMN `generate_status` tinyint NOT NULL DEFAULT 0 COMMENT 'AI生成状态：0-未生成 1-生成中 2-成功 3-失败' AFTER `status`,
  ADD COLUMN `generate_error` varchar(255) NOT NULL DEFAULT '' COMMENT '生成失败脱敏提示' AFTER `generate_status`;

-- ④ 测验题目表：补充难度/知识点/来源文档与原文/本题得分与评分依据
ALTER TABLE `biz_quiz_question`
  ADD COLUMN `difficulty` tinyint NOT NULL DEFAULT 1 COMMENT '难度：1-基础 2-进阶 3-挑战' AFTER `type`,
  ADD COLUMN `knowledge_point` varchar(128) NOT NULL DEFAULT '' COMMENT '对应知识点' AFTER `analysis`,
  ADD COLUMN `source_doc_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT '来源文档' AFTER `source_chunk_id`,
  ADD COLUMN `source_content` text COMMENT '来源原文片段' AFTER `source_doc_id`,
  ADD COLUMN `score` int NOT NULL DEFAULT 0 COMMENT '本题得分（0-100）' AFTER `is_correct`,
  ADD COLUMN `score_basis` text COMMENT '简答 AI 评分依据' AFTER `score`;