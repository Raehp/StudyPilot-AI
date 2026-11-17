package com.studypilot.vo;

import lombok.Data;

import java.util.List;

/** 文档统计：解析/向量化状态分布 + 近 7 日上传趋势（真实数据）。 */
@Data
public class DocumentStatisticsVO {
    private long total;
    private long parsed;
    private long vectorized;
    private long parseFailed;
    private long pending;
    private List<StatItemVO> parseStatusDistribution;
    private List<StatItemVO> vectorStatusDistribution;
    private List<TrendItemVO> uploadTrend;
}