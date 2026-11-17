package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 0-user 1-assistant */
    private Integer role;

    private String content;

    /** 引用数组 JSON 快照，后端组装，AI 不参与生成 */
    private String citations;

    private Integer tokenCount;

    /** 见 MessageStatus：0-生成中 1-成功 2-失败 3-已停止 */
    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}