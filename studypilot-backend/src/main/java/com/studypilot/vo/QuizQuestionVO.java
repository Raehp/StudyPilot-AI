package com.studypilot.vo;

import lombok.Data;

import java.util.List;

/** 题目 VO。练习态隐藏 answer/analysis/sourceContent/userAnswer/isCorrect/score/scoreBasis；结果态完整返回。 */
@Data
public class QuizQuestionVO {

    private Long id;

    /** 1-单选 2-多选 3-判断 4-简答 */
    private Integer type;

    private Integer difficulty;

    private String stem;

    private List<String> options;

    private String knowledgePoint;

    private Long sourceDocId;

    private String sourceDocTitle;

    private String sourceContent;

    private Integer sortOrder;

    private String answer;

    private String analysis;

    private String userAnswer;

    /** -1-未判分 0-错误 1-正确 */
    private Integer isCorrect;

    /** 0-100 */
    private Integer score;

    private String scoreBasis;
}