package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studypilot.common.GenerateStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.config.QuizProperties;
import com.studypilot.dto.QuizSubmitDTO;
import com.studypilot.entity.Quiz;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.QuizMapper;
import com.studypilot.mapper.QuizQuestionMapper;
import com.studypilot.vo.QuizResultVO;
import com.studypilot.vo.ShortAnswerScoreResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 测验判分：客观题（单选/多选/判断）自动判分，多选严格全对得分；
 * 简答题调用通义千问辅助评分（0-100）并给出评分依据，允许查看参考答案。
 * 测验总分为各题得分的平均百分比。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizScoringService {

    private static final int BIZ_TYPE_SCORE = 6;

    private static final String SHORT_ANSWER_SYSTEM = """
            你是高校课程简答题评分助手。依据参考答案要点对用户作答评分，必须客观公正。
            仅输出一个 JSON 对象，包含且仅包含两个字段：
            score（0-100 的整数，按要点覆盖与准确程度给分）、basis（字符串，评分依据，说明命中/缺失了哪些要点）。
            参考答案只是要点参考，用户用不同表述答出同等含义应给分；完全无关或错误应给低分。
            除 JSON 外不要输出任何其他内容。""";

    private final QuizMapper quizMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final QuizStoreService quizStoreService;
    private final DashScopeClient dashScopeClient;
    private final AiOutputParser aiOutputParser;
    private final QuizProperties quizProperties;

    @Transactional
    public QuizResultVO submit(Long quizId, QuizSubmitDTO dto, Long userId) {
        Quiz quiz = quizStoreService.getOwned(quizId);
        if (GenerateStatus.fromCode(quiz.getGenerateStatus()) != GenerateStatus.SUCCESS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "测验尚未生成完成，无法提交");
        }
        if (quiz.getStatus() != null && quiz.getStatus() == 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该测验已提交，如需重练请新建测验");
        }
        List<QuizQuestion> questions = quizStoreService.questions(quizId);
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "测验没有题目，无法提交");
        }
        Map<Long, String> answerMap = new HashMap<>();
        for (QuizSubmitDTO.AnswerItem item : dto.getAnswers()) {
            answerMap.put(item.getQuestionId(), item.getAnswer() == null ? "" : item.getAnswer());
        }
        int scoreSum = 0;
        for (QuizQuestion q : questions) {
            String userAnswer = answerMap.getOrDefault(q.getId(), "");
            if (userAnswer.length() > 500) {
                userAnswer = userAnswer.substring(0, 500);
            }
            int score;
            int isCorrect;
            String basis = "";
            if (q.getType() == 4) {
                ShortAnswerScoreResult graded = gradeShortAnswer(q, userAnswer, userId);
                score = graded.getScore();
                basis = graded.getBasis();
                isCorrect = score >= quizProperties.getShortAnswerPassScore() ? 1 : 0;
            } else {
                boolean correct = objectiveCorrect(q.getType(), q.getAnswer(), userAnswer);
                score = correct ? 100 : 0;
                isCorrect = correct ? 1 : 0;
            }
            scoreSum += score;
            quizQuestionMapper.update(null, new LambdaUpdateWrapper<QuizQuestion>()
                    .eq(QuizQuestion::getId, q.getId())
                    .set(QuizQuestion::getUserAnswer, userAnswer)
                    .set(QuizQuestion::getIsCorrect, isCorrect)
                    .set(QuizQuestion::getScore, score)
                    .set(QuizQuestion::getScoreBasis, basis));
        }
        int avg = (int) Math.round(scoreSum * 1.0 / questions.size());
        quizMapper.update(null, new LambdaUpdateWrapper<Quiz>()
                .eq(Quiz::getId, quizId)
                .set(Quiz::getStatus, 1)
                .set(Quiz::getScore, avg));
        log.info("测验判分完成 quizId={} questions={} score={}", quizId, questions.size(), avg);
        return quizStoreService.toResult(quizMapper.selectById(quizId));
    }

    private boolean objectiveCorrect(int type, String standard, String userRaw) {
        if (userRaw == null || userRaw.isBlank()) {
            return false;
        }
        if (type == 2) {
            return letters(userRaw).equals(letters(standard));
        }
        String user = userRaw.trim().toUpperCase();
        String std = standard == null ? "" : standard.trim().toUpperCase();
        if (user.length() > 1) {
            user = user.substring(0, 1);
        }
        return user.equals(std);
    }

    /** 提取答案中的选项字母，去重升序拼接（兼容 "AC"/"A,C"/"a, c"） */
    private String letters(String raw) {
        TreeSet<Character> set = new TreeSet<>();
        for (char c : raw.toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                set.add(c);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (char c : set) {
            sb.append(c);
        }
        return sb.toString();
    }

    private ShortAnswerScoreResult gradeShortAnswer(QuizQuestion q, String userAnswer, Long userId) {
        ShortAnswerScoreResult empty = new ShortAnswerScoreResult();
        if (userAnswer.isBlank()) {
            empty.setScore(0);
            empty.setBasis("未作答。");
            return empty;
        }
        String userPrompt = "【题干】\n" + q.getStem()
                + "\n\n【参考答案要点】\n" + q.getAnswer()
                + "\n\n【来源原文】\n" + (q.getSourceContent() == null ? "" : q.getSourceContent())
                + "\n\n【用户作答】\n" + userAnswer
                + "\n\n请评分并输出 JSON。";
        String json = dashScopeClient.chatJson(userId, BIZ_TYPE_SCORE, SHORT_ANSWER_SYSTEM, userPrompt);
        ShortAnswerScoreResult result = aiOutputParser.parse(json, ShortAnswerScoreResult.class);
        result.normalize();
        if (result.getBasis().isBlank()) {
            result.setBasis("AI 未给出评分依据。");
        }
        return result;
    }
}