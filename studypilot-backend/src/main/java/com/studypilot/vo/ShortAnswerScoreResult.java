package com.studypilot.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 简答题 AI 辅助评分结构化输出 DTO（必须含评分依据） */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShortAnswerScoreResult {

    /** 0-100 */
    private Integer score;

    private String basis;

    public void normalize() {
        basis = basis == null ? "" : basis.trim();
        if (score == null) {
            score = 0;
        }
        score = Math.max(0, Math.min(score, 100));
    }
}