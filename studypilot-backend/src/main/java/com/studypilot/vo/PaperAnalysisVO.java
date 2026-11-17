package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaperAnalysisVO {

    private Long analysisId;

    private Long documentId;

    private String documentTitle;

    private String model;

    private String status;

    private PaperAnalysisResult result;

    private String summary;

    private Long durationMs;

    private Integer segmentCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}