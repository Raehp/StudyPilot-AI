package com.studypilot.vo;

import lombok.Data;

import java.util.List;

/** 测验统计：练习次数 / 平均分 / 成绩分布 / 题型分布 / 近 7 日练习趋势 / 最近成绩（真实数据）。 */
@Data
public class QuizStatisticsVO {
    private long quizTotal;
    private long practicedTotal;
    private int avgScore;
    private List<StatItemVO> scoreDistribution;
    private List<StatItemVO> questionTypeDistribution;
    private List<TrendItemVO> practiceTrend;
    private List<RecentScore> recentScores;

    @Data
    public static class RecentScore {
        private Long quizId;
        private String title;
        private int score;
        private String createTime;
    }
}