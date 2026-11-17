package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParseStatusVO {

    private Long documentId;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    private Integer pageCount;

    private Integer charCount;

    private Boolean needOcr;

    private String errorMsg;

    private LocalDateTime updateTime;
}