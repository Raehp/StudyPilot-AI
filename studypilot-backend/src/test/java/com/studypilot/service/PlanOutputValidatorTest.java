package com.studypilot.service;

import com.studypilot.entity.StudyPlan;
import com.studypilot.entity.StudyTask;
import com.studypilot.exception.BusinessException;
import com.studypilot.vo.StudyPlanGenerateResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanOutputValidatorTest {

    private final PlanOutputValidator validator = new PlanOutputValidator();

    private StudyPlan plan() {
        StudyPlan p = new StudyPlan();
        p.setStartDate(LocalDate.of(2026, 11, 17));
        p.setEndDate(LocalDate.of(2026, 11, 30));
        return p;
    }

    private StudyPlanGenerateResult.TaskItem task(String title, String date) {
        StudyPlanGenerateResult.TaskItem t = new StudyPlanGenerateResult.TaskItem();
        t.setStage("阶段一");
        t.setTaskDate(date);
        t.setTitle(title);
        t.setContent("详情");
        t.setFocus("重点");
        t.setRecommendedContent("推荐");
        t.setDurationMin(60);
        t.setStageQuiz(false);
        return t;
    }

    private StudyPlanGenerateResult result(StudyPlanGenerateResult.TaskItem... items) {
        StudyPlanGenerateResult r = new StudyPlanGenerateResult();
        r.setTasks(new ArrayList<>(List.of(items)));
        r.setAiAdvice("建议");
        return r;
    }

    @Test
    void validTasksMapped() {
        StudyPlanGenerateResult r = result(task("读论文", "2026-11-18"), task("复现", "2026-11-19"));
        List<StudyTask> tasks = validator.validateAndMap(r, plan(), 60);
        assertEquals(2, tasks.size());
        assertEquals(LocalDate.of(2026, 11, 18), tasks.get(0).getTaskDate());
        assertEquals(0, tasks.get(0).getSortOrder());
        assertEquals(1, tasks.get(1).getSortOrder());
        assertEquals(0, tasks.get(0).getStatus());
        assertEquals(60, tasks.get(0).getDurationMin());
    }

    @Test
    void emptyTasksFails() {
        assertThrows(BusinessException.class, () -> validator.validateAndMap(result(), plan(), 60));
    }

    @Test
    void blankTitleFails() {
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(result(task("  ", "2026-11-18")), plan(), 60));
    }

    @Test
    void dateBeforeStartFails() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> validator.validateAndMap(result(task("早", "2026-11-16")), plan(), 60));
        assertTrue(e.getMessage().contains("早于计划开始日期"));
    }

    @Test
    void dateAfterEndFails() {
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(result(task("晚", "2026-12-01")), plan(), 60));
    }

    @Test
    void badDateFormatFails() {
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(result(task("坏", "2026/11/18")), plan(), 60));
    }

    @Test
    void exceedsMaxTasksFails() {
        StudyPlanGenerateResult.TaskItem[] items = new StudyPlanGenerateResult.TaskItem[3];
        for (int i = 0; i < 3; i++) {
            items[i] = task("任务" + i, "2026-11-18");
        }
        assertThrows(BusinessException.class, () -> validator.validateAndMap(result(items), plan(), 2));
    }

    @Test
    void durationClamped() {
        StudyPlanGenerateResult.TaskItem t = task("超长", "2026-11-18");
        t.setDurationMin(99999);
        List<StudyTask> tasks = validator.validateAndMap(result(t), plan(), 60);
        assertEquals(1440, tasks.get(0).getDurationMin());
    }

    @Test
    void stageQuizFlagMapped() {
        StudyPlanGenerateResult.TaskItem t = task("阶段测验", "2026-11-20");
        t.setStageQuiz(true);
        List<StudyTask> tasks = validator.validateAndMap(result(t), plan(), 60);
        assertEquals(1, tasks.get(0).getStageQuiz());
    }
}