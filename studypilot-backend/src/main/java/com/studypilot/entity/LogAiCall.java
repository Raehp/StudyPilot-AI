package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("log_ai_call")
public class LogAiCall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 1-摘要 2-分析 3-RAG 4-计划 5-出题 6-判分 */
    private Integer bizType;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    /** 0-成功 1-失败 2-超时 3-限流 */
    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}