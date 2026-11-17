package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studypilot.common.GenerateStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.dto.StudyPlanCreateDTO;
import com.studypilot.dto.StudyPlanUpdateDTO;
import com.studypilot.entity.KnowledgeBase;
import com.studypilot.entity.StudyPlan;
import com.studypilot.entity.StudyTask;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.KnowledgeBaseMapper;
import com.studypilot.mapper.StudyPlanMapper;
import com.studypilot.mapper.StudyTaskMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.StudyPlanProgressVO;
import com.studypilot.vo.StudyPlanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanMapper studyPlanMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public StudyPlan getOwned(Long id) {
        StudyPlan plan = studyPlanMapper.selectById(id);
        if (plan == null || !plan.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习计划不存在");
        }
        return plan;
    }

    @Transactional
    public StudyPlanVO create(StudyPlanCreateDTO dto) {
        validateDates(dto.getStartDate(), dto.getEndDate());
        Long kbId = normalizeKb(dto.getKbId());
        StudyPlan plan = new StudyPlan();
        plan.setUserId(UserContext.getUserId());
        plan.setKbId(kbId);
        plan.setTitle(dto.getTitle().trim());
        plan.setGoal(nz(dto.getGoal()));
        plan.setTopic(nz(dto.getTopic()));
        plan.setStartDate(dto.getStartDate());
        plan.setEndDate(dto.getEndDate());
        plan.setDailyMinutes(dto.getDailyMinutes() == null ? 60 : dto.getDailyMinutes());
        plan.setCurrentLevel(nz(dto.getCurrentLevel()));
        plan.setExpectedOutcome(nz(dto.getExpectedOutcome()));
        plan.setStatus(0);
        plan.setGenerateStatus(GenerateStatus.NONE.getCode());
        plan.setGenerateError("");
        plan.setProgress(0);
        plan.setAiAdvice("");
        studyPlanMapper.insert(plan);
        return toVO(plan);
    }

    public List<StudyPlanVO> list() {
        List<StudyPlan> plans = studyPlanMapper.selectList(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, UserContext.getUserId())
                .orderByDesc(StudyPlan::getId));
        return plans.stream().map(this::toVO).toList();
    }

    public StudyPlanVO get(Long id) {
        return toVO(getOwned(id));
    }

    @Transactional
    public StudyPlanVO update(Long id, StudyPlanUpdateDTO dto) {
        StudyPlan plan = getOwned(id);
        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            validateDates(dto.getStartDate() != null ? dto.getStartDate() : plan.getStartDate(),
                    dto.getEndDate() != null ? dto.getEndDate() : plan.getEndDate());
        }
        LambdaUpdateWrapper<StudyPlan> wrapper = new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getId, id);
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            wrapper.set(StudyPlan::getTitle, dto.getTitle().trim());
        }
        if (dto.getGoal() != null) {
            wrapper.set(StudyPlan::getGoal, dto.getGoal());
        }
        if (dto.getTopic() != null) {
            wrapper.set(StudyPlan::getTopic, dto.getTopic());
        }
        if (dto.getKbId() != null) {
            wrapper.set(StudyPlan::getKbId, normalizeKb(dto.getKbId()));
        }
        if (dto.getStartDate() != null) {
            wrapper.set(StudyPlan::getStartDate, dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            wrapper.set(StudyPlan::getEndDate, dto.getEndDate());
        }
        if (dto.getDailyMinutes() != null) {
            wrapper.set(StudyPlan::getDailyMinutes, dto.getDailyMinutes());
        }
        if (dto.getCurrentLevel() != null) {
            wrapper.set(StudyPlan::getCurrentLevel, dto.getCurrentLevel());
        }
        if (dto.getExpectedOutcome() != null) {
            wrapper.set(StudyPlan::getExpectedOutcome, dto.getExpectedOutcome());
        }
        if (dto.getStatus() != null) {
            wrapper.set(StudyPlan::getStatus, dto.getStatus());
        }
        studyPlanMapper.update(null, wrapper);
        return toVO(studyPlanMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        getOwned(id);
        studyTaskMapper.delete(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getPlanId, id));
        studyPlanMapper.deleteById(id);
    }

    public StudyPlanProgressVO progress(Long id) {
        StudyPlan plan = getOwned(id);
        int[] counts = countTasks(id);
        StudyPlanProgressVO vo = new StudyPlanProgressVO();
        vo.setPlanId(id);
        vo.setTotal(counts[0]);
        vo.setCompleted(counts[1]);
        vo.setProgress(plan.getProgress());
        vo.setStatus(plan.getStatus());
        return vo;
    }

    /** 重新计算并持久化进度；非放弃态下全部完成自动标记已完成 */
    @Transactional
    public int recalcProgress(Long planId) {
        StudyPlan plan = studyPlanMapper.selectById(planId);
        if (plan == null) {
            return 0;
        }
        int[] counts = countTasks(planId);
        int total = counts[0];
        int completed = counts[1];
        int progress = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        LambdaUpdateWrapper<StudyPlan> wrapper = new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .set(StudyPlan::getProgress, progress);
        if (plan.getStatus() != null && plan.getStatus() != 2) {
            int newStatus = (total > 0 && completed == total) ? 1 : 0;
            wrapper.set(StudyPlan::getStatus, newStatus);
        }
        studyPlanMapper.update(null, wrapper);
        return progress;
    }

    /** 生成成功后整体替换任务并写入 AI 建议（事务内：先清旧任务再插新任务） */
    @Transactional
    public void replaceTasks(Long planId, Long userId, List<StudyTask> tasks, String aiAdvice) {
        studyTaskMapper.delete(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getPlanId, planId));
        for (StudyTask task : tasks) {
            task.setPlanId(planId);
            task.setUserId(userId);
            studyTaskMapper.insert(task);
        }
        studyPlanMapper.update(null, new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .set(StudyPlan::getAiAdvice, aiAdvice == null ? "" : aiAdvice)
                .set(StudyPlan::getGenerateStatus, GenerateStatus.SUCCESS.getCode())
                .set(StudyPlan::getGenerateError, "")
                .set(StudyPlan::getProgress, 0)
                .set(StudyPlan::getStatus, 0));
    }

    public void markGenerateFailed(Long planId, String message) {
        studyPlanMapper.update(null, new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .set(StudyPlan::getGenerateStatus, GenerateStatus.FAILED.getCode())
                .set(StudyPlan::getGenerateError, message));
    }

    public String kbName(Long kbId) {
        if (kbId == null || kbId == 0) {
            return "";
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        return kb == null ? "" : kb.getName();
    }

    private Long normalizeKb(Long kbId) {
        if (kbId == null || kbId <= 0) {
            return 0L;
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在");
        }
        return kbId;
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
    }

    private int[] countTasks(Long planId) {
        Long total = studyTaskMapper.selectCount(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getPlanId, planId));
        Long completed = studyTaskMapper.selectCount(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getPlanId, planId)
                .eq(StudyTask::getStatus, 1));
        return new int[]{total.intValue(), completed.intValue()};
    }

    private StudyPlanVO toVO(StudyPlan plan) {
        int[] counts = countTasks(plan.getId());
        StudyPlanVO vo = new StudyPlanVO();
        vo.setId(plan.getId());
        vo.setTitle(plan.getTitle());
        vo.setGoal(plan.getGoal());
        vo.setTopic(plan.getTopic());
        vo.setKbId(plan.getKbId());
        vo.setKbName(kbName(plan.getKbId()));
        vo.setStartDate(plan.getStartDate());
        vo.setEndDate(plan.getEndDate());
        vo.setDailyMinutes(plan.getDailyMinutes());
        vo.setCurrentLevel(plan.getCurrentLevel());
        vo.setExpectedOutcome(plan.getExpectedOutcome());
        vo.setStatus(plan.getStatus());
        vo.setGenerateStatus(GenerateStatus.fromCode(plan.getGenerateStatus()).getExternalName());
        vo.setGenerateError(plan.getGenerateError());
        vo.setProgress(plan.getProgress());
        vo.setAiAdvice(plan.getAiAdvice());
        vo.setTaskCount(counts[0]);
        vo.setCompletedCount(counts[1]);
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());
        return vo;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}