package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "studypilot.quiz")
public class QuizProperties {

    /** 出题检索 Top-K */
    private int retrievalTopK = 8;

    /** 检索最低相似度阈值 */
    private double retrievalMinScore = 0.45;

    /** 检索内容进入 Prompt 的总字符预算 */
    private int maxContextChars = 8000;

    /** 题目数量下限 */
    private int minQuestions = 1;

    /** 题目数量上限 */
    private int maxQuestions = 20;

    /** 默认题目数量 */
    private int defaultQuestionCount = 5;

    /** 简答题视为掌握（非错题）的最低得分 */
    private int shortAnswerPassScore = 60;

    /** 单次生成硬 deadline */
    private long maxGenerationMs = 180000;
}