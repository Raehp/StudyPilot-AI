package com.studypilot.vo;

import lombok.Data;

@Data
public class VectorSearchResultVO {

    private Long chunkId;

    private String chunkUid;

    private Long documentId;

    private String documentTitle;

    private String content;

    private Integer pageNum;

    private String sourcePosition;

    /** 余弦相似度 [-1,1] */
    private Double score;
}