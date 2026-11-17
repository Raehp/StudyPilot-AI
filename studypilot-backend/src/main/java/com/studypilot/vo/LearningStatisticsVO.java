package com.studypilot.vo;

import lombok.Data;

import java.util.List;

/** 学习统计：计划进度 + 任务完成趋势 + 近 7 日综合学习活跃趋势（真实数据）。 */
@Data
public class LearningStatisticsVO {
    private long planTotal;
    private long taskTotal;
    private long completedTaskTotal;
    private int avgProgress;
    private List<PlanProgress> planProgressList;
    private List<TrendItemVO> completionTrend;
    private ActivityTrend activityTrend;

    @Data
    public static class PlanProgress {
        private Long planId;
        private String title;
        private int progress;
        private long total;
        private long completed;
    }

    /** 近 7 日多系列活跃趋势：文档上传 / 任务完成 / 测验练习 / 问答会话。 */
    @Data
    public static class ActivityTrend {
        private List<String> dates;
        private List<Long> documents;
        private List<Long> tasks;
        private List<Long> quizzes;
        private List<Long> conversations;
    }
}