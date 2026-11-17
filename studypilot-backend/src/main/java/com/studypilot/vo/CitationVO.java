package com.studypilot.vo;

import lombok.Data;

/** 引用溯源：全部字段由后端从真实检索结果组装，AI 不得自行生成 */
@Data
public class CitationVO {

    private Long documentId;

    private String documentName;

    private Long chunkId;

    private String chunkUid;

    private Integer pageNum;

    private String originalContent;

    private Double similarityScore;
}