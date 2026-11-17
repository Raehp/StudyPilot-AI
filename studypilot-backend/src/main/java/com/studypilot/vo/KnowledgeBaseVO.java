package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseVO {

    private Long id;

    private String name;

    private String description;

    private Integer docCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}