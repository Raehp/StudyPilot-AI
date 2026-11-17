package com.studypilot.service;

import com.studypilot.common.GenerateStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.config.QuizProperties;
import com.studypilot.entity.Quiz;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.QuizMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.GenerateStatusVO;
import com.studypilot.vo.QuizGenerateResult;
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
 * 智能出题 AI 异步生成器：基于知识库真实检索内容命题，经结构化/数量/答案/引用校验后才落库。
 * 检索为空或输出不合规即落脱敏失败终态，绝不保存无法解析的题目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizGenerator {

    private static final String LOCK_PREFIX = "studypilot:quiz:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final int BIZ_TYPE_QUIZ = 5;

    private final QuizMapper quizMapper;
    private final QuizStoreService quizStoreService;
    private final RagRetrievalService ragRetrievalService;
    private final DashScopeClient dashScopeClient;
    private final QuizPromptBuilder quizPromptBuilder;
    private final AiOutputParser aiOutputParser;
    private final QuizOutputValidator quizOutputValidator;
    private final QuizProperties quizProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public GenerateStatusVO submit(Long quizId) {
        Quiz quiz = quizStoreService.getOwned(quizId);
        if (GenerateStatus.fromCode(quiz.getGenerateStatus()) == GenerateStatus.GENERATING) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "测验正在生成中，请勿重复提交");
        }
        String lockKey = LOCK_PREFIX + quizId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "测验正在生成中，请勿重复提交");
        }
        quizStoreService.setGenerating(quizId);
        try {
            generateAsync(quizId, UserContext.getUserId(), lockKey);
        } catch (TaskRejectedException e) {
            quizStoreService.markFailed(quizId, "生成任务繁忙，请稍后重试");
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "生成任务繁忙，请稍后重试");
        }
        return statusVO(quizMapper.selectById(quizId));
    }

    public GenerateStatusVO status(Long quizId) {
        quizStoreService.getOwned(quizId);
        return statusVO(quizMapper.selectById(quizId));
    }

    @Async("planQuizExecutor")
    public void generateAsync(Long quizId, Long userId, String lockKey) {
        long start = System.currentTimeMillis();
        try {
            Quiz quiz = quizMapper.selectById(quizId);
            if (quiz == null || GenerateStatus.fromCode(quiz.getGenerateStatus()) != GenerateStatus.GENERATING) {
                log.warn("测验生成状态已变化，跳过: quizId={}", quizId);
                return;
            }
            String query = quiz.getTopic() == null || quiz.getTopic().isBlank()
                    ? quizStoreService.kbName(quiz.getKbId()) : quiz.getTopic();
            List<VectorSearchResultVO> chunks = ragRetrievalService.retrieve(quiz.getKbId(), query,
                    quizProperties.getRetrievalTopK(), quizProperties.getRetrievalMinScore());
            if (chunks.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "知识库中未找到可用于出题的内容，请先上传并向量化文档");
            }
            List<Integer> types = quizStoreService.parseTypes(quiz.getQuestionTypes());
            String[] prompt = quizPromptBuilder.build(quiz.getTopic(), quiz.getQuestionCount(),
                    quiz.getDifficulty(), types, chunks);
            String json = dashScopeClient.chatJson(userId, BIZ_TYPE_QUIZ, prompt[0], prompt[1]);
            QuizGenerateResult result = aiOutputParser.parse(json, QuizGenerateResult.class);
            result.normalize();
            List<QuizQuestion> questions = quizOutputValidator.validateAndMap(result, quiz.getQuestionCount(),
                    types, quiz.getDifficulty(), chunks);
            quizStoreService.replaceQuestions(quizId, questions);
            log.info("智能出题成功 quizId={} questions={} grounded={} latencyMs={}",
                    quizId, questions.size(), chunks.size(), System.currentTimeMillis() - start);
        } catch (BusinessException e) {
            quizStoreService.markFailed(quizId, e.getMessage());
            log.warn("智能出题失败 quizId={} msg={}", quizId, e.getMessage());
        } catch (Exception e) {
            log.error("智能出题异常 quizId={}", quizId, e);
            quizStoreService.markFailed(quizId, "AI 出题失败，请稍后重试");
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private GenerateStatusVO statusVO(Quiz quiz) {
        GenerateStatusVO vo = new GenerateStatusVO();
        vo.setId(quiz.getId());
        vo.setStatus(GenerateStatus.fromCode(quiz.getGenerateStatus()).getExternalName());
        vo.setErrorMsg(quiz.getGenerateError());
        vo.setUpdateTime(quiz.getUpdateTime());
        return vo;
    }
}