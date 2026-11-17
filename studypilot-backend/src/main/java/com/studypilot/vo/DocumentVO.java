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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}