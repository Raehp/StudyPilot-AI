package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VectorStatusVO {

    private Long documentId;

    /** null=从未处理；PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    private Integer chunkCount;

    private Integer embeddingCount;

    /** 0-100，embeddingCount/chunkCount */
    private Integer progress;

    private String errorMsg;

    private LocalDateTime updateTime;
}