package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_paper_analysis")
public class PaperAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long userId;

    private String model;

    /** 0-PENDING 1-PROCESSING 2-SUCCESS 3-FAILED */
    private Integer status;

    private String errorMsg;

    /** 12字段结构化分析结果JSON */
    private String result;

    private String summary;

    private Long durationMs;

    private Integer segmentCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}