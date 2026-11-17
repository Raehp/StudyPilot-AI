package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识库，0=未归属文档池 */
    private Long kbId;

    private Long userId;

    private String title;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private Integer pageCount;

    private Integer charCount;

    private Integer needOcr;

    private Integer status;

    private String errorMsg;

    private String summary;

    private String analysis;

    private Integer analyzed;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}