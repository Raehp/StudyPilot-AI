package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studypilot.common.GenerateStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.config.PlanProperties;
import com.studypilot.entity.StudyPlan;
import com.studypilot.entity.StudyTask;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.StudyPlanMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.GenerateStatusVO;
import com.studypilot.vo.StudyPlanGenerateResult;
import com.studypilot.vo.VectorSearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 学习计划 AI 异步生成器：复用 M3/M4 任务状态机（GENERATING→SUCCESS/FAILED）+ Redis 防重。
 * 可结合用户知识库真实检索内容；生成失败落脱敏终态，绝不保存无法解析的输出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyPlanGenerator {

    private static final String LOCK_PREFIX = "studypilot:plan:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final int BIZ_TYPE_PLAN = 4;

    private final StudyPlanMapper studyPlanMapper;
    private final StudyPlanService studyPlanService;
    private final RagRetrievalService ragRetrievalService;
    private final DashScopeClient dashScopeClient;
    private final PlanPromptBuilder planPromptBuilder;
    private final AiOutputParser aiOutputParser;
    private final PlanOutputValidator planOutputValidator;
    private final PlanProperties planProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public GenerateStatusVO submit(Long planId) {
        StudyPlan plan = studyPlanService.getOwned(planId);
        if (GenerateStatus.fromCode(plan.getGenerateStatus()) == GenerateStatus.GENERATING) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "计划正在生成中，请勿重复提交");
        }
        String lockKey = LOCK_PREFIX + planId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "计划正在生成中，请勿重复提交");
        }
        studyPlanMapper.update(null, new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .set(StudyPlan::getGenerateStatus, GenerateStatus.GENERATING.getCode())
                .set(StudyPlan::getGenerateError, ""));
        try {
            generateAsync(planId, UserContext.getUserId(), lockKey);
        } catch (TaskRejectedException e) {
            studyPlanService.markGenerateFailed(planId, "生成任务繁忙，请稍后重试");
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "生成任务繁忙，请稍后重试");
        }
        return statusVO(studyPlanMapper.selectById(planId));
    }

    public GenerateStatusVO status(Long planId) {
        studyPlanService.getOwned(planId);
        return statusVO(studyPlanMapper.selectById(planId));
    }

    @Async("planQuizExecutor")
    public void generateAsync(Long planId, Long userId, String lockKey) {
        long start = System.currentTimeMillis();
        try {
            StudyPlan plan = studyPlanMapper.selectById(planId);
            if (plan == null || GenerateStatus.fromCode(plan.getGenerateStatus()) != GenerateStatus.GENERATING) {
                log.warn("计划生成状态已变化，跳过: planId={}", planId);
                return;
            }
            List<VectorSearchResultVO> chunks = List.of();
            if (plan.getKbId() != null && plan.getKbId() > 0) {
                String query = plan.getTopic() == null || plan.getTopic().isBlank()
                        ? plan.getGoal() : plan.getTopic();
                if (query != null && !query.isBlank()) {
                    chunks = ragRetrievalService.retrieve(plan.getKbId(), query,
                            planProperties.getRetrievalTopK(), planProperties.getRetrievalMinScore());
                }
            }
            String[] prompt = planPromptBuilder.build(plan, chunks);
            String json = dashScopeClient.chatJson(userId, BIZ_TYPE_PLAN, prompt[0], prompt[1]);
            StudyPlanGenerateResult result = aiOutputParser.parse(json, StudyPlanGenerateResult.class);
            result.normalize();
            List<StudyTask> tasks = planOutputValidator.validateAndMap(result, plan, planProperties.getMaxTasks());
            studyPlanService.replaceTasks(planId, userId, tasks, result.getAiAdvice());
            log.info("学习计划生成成功 planId={} tasks={} grounded={} latencyMs={}",
                    planId, tasks.size(), chunks.size(), System.currentTimeMillis() - start);
        } catch (BusinessException e) {
            studyPlanService.markGenerateFailed(planId, e.getMessage());
            log.warn("学习计划生成失败 planId={} msg={}", planId, e.getMessage());
        } catch (Exception e) {
            log.error("学习计划生成异常 planId={}", planId, e);
            studyPlanService.markGenerateFailed(planId, "AI 生成失败，请稍后重试");
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private GenerateStatusVO statusVO(StudyPlan plan) {
        GenerateStatusVO vo = new GenerateStatusVO();
        vo.setId(plan.getId());
        vo.setStatus(GenerateStatus.fromCode(plan.getGenerateStatus()).getExternalName());
        vo.setErrorMsg(plan.getGenerateError());
        vo.setUpdateTime(plan.getUpdateTime());
        return vo;
    }
}