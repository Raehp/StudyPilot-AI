package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_quiz")
public class Quiz {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long docId;

    private Long kbId;

    private String topic;

    private String title;

    /** 1-基础 2-进阶 3-挑战 */
    private Integer difficulty;

    /** 逗号分隔题型：1单选 2多选 3判断 4简答 */
    private String questionTypes;

    private Integer questionCount;

    /** 0-未作答 1-已提交 */
    private Integer status;

    /** 0-未生成 1-生成中 2-成功 3-失败 */
    private Integer generateStatus;

    private String generateError;

    /** 0-100 百分比 */
    private Integer score;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}