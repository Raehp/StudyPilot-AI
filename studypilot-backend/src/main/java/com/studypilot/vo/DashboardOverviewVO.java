package com.studypilot.vo;

import lombok.Data;

/** 工作台总览：9 项真实统计（全部来自当前用户 MySQL 数据，无记录为 0）。 */
@Data
public class DashboardOverviewVO {
    /** 用户文档总数 */
    private long documentTotal;
    /** 知识库总数 */
    private long knowledgeBaseTotal;
    /** 已解析文档数（status=READY） */
    private long parsedDocumentTotal;
    /** 已向量化文档数（vector_status=SUCCESS） */
    private long vectorizedDocumentTotal;
    /** 聊天会话数量 */
    private long conversationTotal;
    /** 学习计划数量 */
    private long studyPlanTotal;
    /** 已完成学习任务数量（status=1） */
    private long completedTaskTotal;
    /** 学习任务总数（用于进度对照） */
    private long totalTaskCount;
    /** 练习次数（已提交测验数 status=1） */
    private long quizPracticeTotal;
    /** 学习进度统计：学习计划平均进度 0-100 */
    private int learningProgress;
}