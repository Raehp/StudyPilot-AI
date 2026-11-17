package com.studypilot.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 通义千问学习计划生成结构化输出 DTO（不直接信任，需经校验） */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudyPlanGenerateResult {

    private String aiAdvice;

    private List<TaskItem> tasks;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskItem {
        private String stage;
        /** yyyy-MM-dd */
        private String taskDate;
        private String title;
        private String content;
        private String focus;
        private String recommendedContent;
        private Integer durationMin;
        private Boolean stageQuiz;
    }

    public void normalize() {
        aiAdvice = trim(aiAdvice);
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        for (TaskItem t : tasks) {
            if (t == null) {
                continue;
            }
            t.setStage(trim(t.getStage()));
            t.setTaskDate(trim(t.getTaskDate()));
            t.setTitle(trim(t.getTitle()));
            t.setContent(trim(t.getContent()));
            t.setFocus(trim(t.getFocus()));
            t.setRecommendedContent(trim(t.getRecommendedContent()));
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}