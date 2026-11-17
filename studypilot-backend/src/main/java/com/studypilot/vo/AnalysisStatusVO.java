package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalysisStatusVO {

    /** null = 该文档从未发起过分析 */
    private Long analysisId;

    private Long documentId;

    private String status;

    private String model;

    private String errorMsg;

    private Long durationMs;

    private Integer segmentCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}