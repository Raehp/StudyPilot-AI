package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "studypilot.plan")
public class PlanProperties {

    /** 结合知识库生成计划时的检索 Top-K */
    private int retrievalTopK = 6;

    /** 检索最低相似度阈值 */
    private double retrievalMinScore = 0.45;

    /** 检索内容进入 Prompt 的总字符预算 */
    private int maxContextChars = 6000;

    /** 单次生成任务数上限（防止模型超量输出） */
    private int maxTasks = 60;

    /** 单次生成硬 deadline */
    private long maxGenerationMs = 180000;
}