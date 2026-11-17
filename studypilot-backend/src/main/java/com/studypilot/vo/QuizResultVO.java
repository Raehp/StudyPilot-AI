package com.studypilot.vo;

import lombok.Data;

import java.util.List;

/** 测验结果（含答案解析、判分与简答评分依据） */
@Data
public class QuizResultVO {

    private Long quizId;

    private String title;

    /** 0-100 百分比 */
    private Integer score;

    private Integer questionCount;

    private Integer correctCount;

    /** 0-未作答 1-已提交 */
    private Integer status;

    private List<QuizQuestionVO> questions;
}