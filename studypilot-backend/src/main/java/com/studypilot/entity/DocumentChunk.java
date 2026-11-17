package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_document_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 稳定唯一标识 SHA-256(documentId:chunkIndex:contentHash) */
    private String chunkUid;

    private Long docId;

    private Long kbId;

    private Integer chunkIndex;

    private Integer pageNum;

    /** 原文位置 p{页}:{起}-{止} */
    private String sourcePosition;

    private String content;

    private Integer charCount;

    /** 向量 JSON 数组字符串，NULL=尚未嵌入 */
    private String embedding;

    private String embeddingModel;

    private Integer embeddingDimension;

    private String embeddingVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}