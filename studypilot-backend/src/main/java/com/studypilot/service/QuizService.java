package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studypilot.common.ResultCode;
import com.studypilot.config.QuizProperties;
import com.studypilot.dto.QuizGenerateDTO;
import com.studypilot.dto.QuizSubmitDTO;
import com.studypilot.entity.Quiz;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.QuizQuestionMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.GenerateStatusVO;
import com.studypilot.vo.QuizDetailVO;
import com.studypilot.vo.QuizListItemVO;
import com.studypilot.vo.QuizResultVO;
import com.studypilot.vo.WrongAnswerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizStoreService quizStoreService;
    private final QuizGenerator quizGenerator;
    private final QuizScoringService quizScoringService;
    private final QuizQuestionMapper quizQuestionMapper;
    private final QuizProperties quizProperties;

    public GenerateStatusVO generate(QuizGenerateDTO dto) {
        int count = dto.getQuestionCount() == null ? quizProperties.getDefaultQuestionCount()
                : Math.max(quizProperties.getMinQuestions(), Math.min(dto.getQuestionCount(), quizProperties.getMaxQuestions()));
        int difficulty = dto.getDifficulty() == null ? 1 : dto.getDifficulty();
        Quiz quiz = quizStoreService.createShell(dto, count, difficulty);
        return quizGenerator.submit(quiz.getId());
    }

    public List<QuizListItemVO> list() {
        return quizStoreService.listOwned().stream().map(quizStoreService::toListItem).toList();
    }

    public QuizDetailVO detail(Long id) {
        return quizStoreService.toDetail(quizStoreService.getOwned(id));
    }

    public GenerateStatusVO generateStatus(Long id) {
        return quizGenerator.status(id);
    }

    public QuizResultVO submit(Long id, QuizSubmitDTO dto) {
        return quizScoringService.submit(id, dto, UserContext.getUserId());
    }

    public QuizResultVO result(Long id) {
        Quiz quiz = quizStoreService.getOwned(id);
        if (quiz.getStatus() == null || quiz.getStatus() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "尚未提交测验，暂无成绩");
        }
        return quizStoreService.toResult(quiz);
    }

    public void delete(Long id) {
        quizStoreService.getOwned(id);
        quizStoreService.delete(id);
    }

    /** 错题回顾：客观题答错 + 简答得分低于掌握阈值，仅限本人已提交测验 */
    public List<WrongAnswerVO> wrongAnswers() {
        List<Quiz> submitted = quizStoreService.listOwned().stream()
                .filter(q -> q.getStatus() != null && q.getStatus() == 1).toList();
        if (submitted.isEmpty()) {
            return List.of();
        }
        Map<Long, String> quizTitles = new HashMap<>();
        List<Long> quizIds = new ArrayList<>();
        for (Quiz q : submitted) {
            quizIds.add(q.getId());
            quizTitles.put(q.getId(), q.getTitle());
        }
        int pass = quizProperties.getShortAnswerPassScore();
        List<QuizQuestion> wrong = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .in(QuizQuestion::getQuizId, quizIds)
                .and(w -> w.eq(QuizQuestion::getIsCorrect, 0)
                        .or(o -> o.eq(QuizQuestion::getType, 4).lt(QuizQuestion::getScore, pass)))
                .orderByDesc(QuizQuestion::getQuizId)
                .orderByAsc(QuizQuestion::getSortOrder));
        Map<Long, String> titles = quizStoreService.docTitles(wrong);
        List<WrongAnswerVO> result = new ArrayList<>();
        for (QuizQuestion q : wrong) {
            WrongAnswerVO vo = new WrongAnswerVO();
            vo.setQuestionId(q.getId());
            vo.setQuizId(q.getQuizId());
            vo.setQuizTitle(quizTitles.getOrDefault(q.getQuizId(), ""));
            vo.setType(q.getType());
            vo.setStem(q.getStem());
            vo.setOptions(quizStoreService.parseOptions(q.getOptions()));
            vo.setAnswer(q.getAnswer());
            vo.setAnalysis(q.getAnalysis());
            vo.setUserAnswer(q.getUserAnswer());
            vo.setIsCorrect(q.getIsCorrect());
            vo.setScore(q.getScore());
            vo.setScoreBasis(q.getScoreBasis());
            vo.setKnowledgePoint(q.getKnowledgePoint());
            vo.setSourceDocId(q.getSourceDocId());
            vo.setSourceDocTitle(titles.getOrDefault(q.getSourceDocId(), ""));
            vo.setSourceContent(q.getSourceContent());
            vo.setCreateTime(q.getCreateTime());
            result.add(vo);
        }
        return result;
    }
}