package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long id;

    private Long kbId;

    private String kbName;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}