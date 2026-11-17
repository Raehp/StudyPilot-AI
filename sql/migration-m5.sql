-- M5 迁移：聊天消息流式终态字段（已获用户批准 2026-11-17）
-- 仅新增列，不改动已有列与数据
ALTER TABLE `biz_chat_message`
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '消息状态：0-生成中 1-成功 2-失败 3-已停止' AFTER `token_count`,
  ADD COLUMN `error_msg` varchar(255) NOT NULL DEFAULT '' COMMENT '失败/停止时的脱敏提示信息' AFTER `status`;