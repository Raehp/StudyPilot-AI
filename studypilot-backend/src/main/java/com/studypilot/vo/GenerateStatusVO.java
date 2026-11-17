package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** AI 异步生成状态轮询 VO（学习计划 / 智能出题共用） */
@Data
public class GenerateStatusVO {

    private Long id;

    /** NONE / GENERATING / SUCCESS / FAILED */
    private String status;

    private String errorMsg;

    private LocalDateTime updateTime;
}