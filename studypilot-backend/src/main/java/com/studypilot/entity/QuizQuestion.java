package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_quiz_question")
public class QuizQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quizId;

    /** 1-单选 2-多选 3-判断 4-简答 */
    private Integer type;

    /** 1-基础 2-进阶 3-挑战 */
    private Integer difficulty;

    private String stem;

    /** 选项 JSON 数组字符串 */
    private String options;

    private String answer;

    private String analysis;

    private String knowledgePoint;

    private Long sourceChunkId;

    private Long sourceDocId;

    private String sourceContent;

    private String userAnswer;

    /** -1-未判分 0-错误 1-正确 */
    private Integer isCorrect;

    /** 本题得分 0-100 */
    private Integer score;

    private String scoreBasis;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}