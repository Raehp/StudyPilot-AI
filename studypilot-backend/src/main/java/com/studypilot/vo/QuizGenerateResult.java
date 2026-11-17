package com.studypilot.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 通义千问出题结构化输出 DTO（不直接信任，需经数量/答案/引用校验） */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuizGenerateResult {

    private List<QuestionItem> questions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionItem {
        /** 1-单选 2-多选 3-判断 4-简答 */
        private Integer type;
        private Integer difficulty;
        private String stem;
        private List<String> options;
        private String answer;
        private String analysis;
        private String knowledgePoint;
        /** 引用检索片段序号（0 起），由后端映射真实 chunk，杜绝伪造来源 */
        private Integer sourceIndex;
    }

    public void normalize() {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        for (QuestionItem q : questions) {
            if (q == null) {
                continue;
            }
            q.setStem(trim(q.getStem()));
            q.setAnswer(trim(q.getAnswer()));
            q.setAnalysis(trim(q.getAnalysis()));
            q.setKnowledgePoint(trim(q.getKnowledgePoint()));
            if (q.getOptions() == null) {
                q.setOptions(new ArrayList<>());
            }
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}