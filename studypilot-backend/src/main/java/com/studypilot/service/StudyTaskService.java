package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studypilot.common.ResultCode;
import com.studypilot.dto.StudyTaskUpdateDTO;
import com.studypilot.entity.StudyTask;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.StudyTaskMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.StudyTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyTaskService {

    private final StudyTaskMapper studyTaskMapper;
    private final StudyPlanService studyPlanService;

    public List<StudyTaskVO> listByPlan(Long planId) {
        studyPlanService.getOwned(planId);
        List<StudyTask> tasks = studyTaskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getPlanId, planId)
                .orderByAsc(StudyTask::getTaskDate)
                .orderByAsc(StudyTask::getSortOrder)
                .orderByAsc(StudyTask::getId));
        return tasks.stream().map(this::toVO).toList();
    }

    @Transactional
    public StudyTaskVO updateStatus(Long taskId, Integer status) {
        StudyTask task = getOwned(taskId);
        studyTaskMapper.update(null, new LambdaUpdateWrapper<StudyTask>()
                .eq(StudyTask::getId, taskId)
                .set(StudyTask::getStatus, status));
        studyPlanService.recalcProgress(task.getPlanId());
        return toVO(studyTaskMapper.selectById(taskId));
    }

    @Transactional
    public StudyTaskVO update(Long taskId, StudyTaskUpdateDTO dto) {
        getOwned(taskId);
        LambdaUpdateWrapper<StudyTask> wrapper = new LambdaUpdateWrapper<StudyTask>()
                .eq(StudyTask::getId, taskId);
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            wrapper.set(StudyTask::getTitle, dto.getTitle().trim());
        }
        if (dto.getContent() != null) {
            wrapper.set(StudyTask::getContent, dto.getContent());
        }
        if (dto.getFocus() != null) {
            wrapper.set(StudyTask::getFocus, dto.getFocus());
        }
        if (dto.getRecommendedContent() != null) {
            wrapper.set(StudyTask::getRecommendedContent, dto.getRecommendedContent());
        }
        if (dto.getStage() != null) {
            wrapper.set(StudyTask::getStage, dto.getStage());
        }
        if (dto.getTaskDate() != null) {
            wrapper.set(StudyTask::getTaskDate, dto.getTaskDate());
        }
        if (dto.getDurationMin() != null) {
            wrapper.set(StudyTask::getDurationMin, dto.getDurationMin());
        }
        if (dto.getStageQuiz() != null) {
            wrapper.set(StudyTask::getStageQuiz, dto.getStageQuiz());
        }
        if (dto.getSortOrder() != null) {
            wrapper.set(StudyTask::getSortOrder, dto.getSortOrder());
        }
        studyTaskMapper.update(null, wrapper);
        return toVO(studyTaskMapper.selectById(taskId));
    }

    private StudyTask getOwned(Long id) {
        StudyTask task = studyTaskMapper.selectById(id);
        if (task == null || !task.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习任务不存在");
        }
        return task;
    }

    private StudyTaskVO toVO(StudyTask task) {
        StudyTaskVO vo = new StudyTaskVO();
        vo.setId(task.getId());
        vo.setPlanId(task.getPlanId());
        vo.setStage(task.getStage());
        vo.setTaskDate(task.getTaskDate());
        vo.setTitle(task.getTitle());
        vo.setContent(task.getContent());
        vo.setFocus(task.getFocus());
        vo.setRecommendedContent(task.getRecommendedContent());
        vo.setDurationMin(task.getDurationMin());
        vo.setStageQuiz(task.getStageQuiz() != null && task.getStageQuiz() == 1);
        vo.setStatus(task.getStatus());
        vo.setSortOrder(task.getSortOrder());
        return vo;
    }
}