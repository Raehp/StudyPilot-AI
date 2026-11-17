package com.studypilot.service;

import com.studypilot.common.ResultCode;
import com.studypilot.entity.StudyPlan;
import com.studypilot.entity.StudyTask;
import com.studypilot.exception.BusinessException;
import com.studypilot.vo.StudyPlanGenerateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习计划生成结果校验器：不直接信任大模型输出。
 * 校验任务非空、数量上限、标题非空、日期可解析且落在计划周期内、时长合理；
 * 全部通过才映射为可持久化任务，任一不合法即整体失败。
 */
@Slf4j
@Service
public class PlanOutputValidator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<StudyTask> validateAndMap(StudyPlanGenerateResult result, StudyPlan plan, int maxTasks) {
        List<StudyPlanGenerateResult.TaskItem> items = result.getTasks();
        if (items == null || items.isEmpty()) {
            throw fail("AI 未生成任何学习任务，请重试");
        }
        if (items.size() > maxTasks) {
            throw fail("AI 生成任务数量超出上限（" + maxTasks + "），请重试");
        }
        List<StudyTask> tasks = new ArrayList<>();
        int order = 0;
        for (int i = 0; i < items.size(); i++) {
            StudyPlanGenerateResult.TaskItem item = items.get(i);
            String label = "第" + (i + 1) + "个任务";
            if (item == null || isBlank(item.getTitle())) {
                throw fail(label + "标题为空，请重试");
            }
            LocalDate date = parseDate(item.getTaskDate(), label);
            if (date != null && plan.getStartDate() != null && date.isBefore(plan.getStartDate())) {
                throw fail(label + "日期早于计划开始日期，请重试");
            }
            if (date != null && plan.getEndDate() != null && date.isAfter(plan.getEndDate())) {
                throw fail(label + "日期晚于计划结束日期，请重试");
            }
            StudyTask task = new StudyTask();
            task.setStage(nz(item.getStage()));
            task.setTaskDate(date);
            task.setTitle(truncate(item.getTitle().trim(), 128));
            task.setContent(truncate(nz(item.getContent()), 1000));
            task.setFocus(truncate(nz(item.getFocus()), 500));
            task.setRecommendedContent(truncate(nz(item.getRecommendedContent()), 1000));
            task.setDurationMin(clampDuration(item.getDurationMin()));
            task.setStageQuiz(Boolean.TRUE.equals(item.getStageQuiz()) ? 1 : 0);
            task.setStatus(0);
            task.setSortOrder(order++);
            tasks.add(task);
        }
        return tasks;
    }

    private LocalDate parseDate(String text, String label) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE);
        } catch (Exception e) {
            throw fail(label + "日期格式非法（应为 yyyy-MM-dd），请重试");
        }
    }

    private int clampDuration(Integer minutes) {
        if (minutes == null) {
            return 0;
        }
        return Math.max(0, Math.min(minutes, 1440));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BusinessException fail(String message) {
        return new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
    }
}