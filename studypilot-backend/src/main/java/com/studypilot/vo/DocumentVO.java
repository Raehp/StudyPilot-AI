package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {

    private Long id;

    private Long kbId;

    private String title;

    private String fileName;

    private Long fileSize;

    private Integer pageCount;

    private Integer charCount;

    private Boolean needOcr;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    private String errorMsg;

    /** 向量化状态 PENDING / PROCESSING / SUCCESS / FAILED */
    private String vectorStatus;

    private Integer chunkCount;

    private Integer embeddingCount;

    private String vectorErrorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}