package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatMessageVO {

    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    private String content;

    private List<CitationVO> citations;

    private Integer tokenCount;

    /** GENERATING / SUCCESS / FAILED / STOPPED */
    private String status;

    private String errorMsg;

    private LocalDateTime createTime;
}