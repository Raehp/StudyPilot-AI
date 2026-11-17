package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.GenerateStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.dto.QuizGenerateDTO;
import com.studypilot.entity.Document;
import com.studypilot.entity.KnowledgeBase;
import com.studypilot.entity.Quiz;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.mapper.KnowledgeBaseMapper;
import com.studypilot.mapper.QuizMapper;
import com.studypilot.mapper.QuizQuestionMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.QuizDetailVO;
import com.studypilot.vo.QuizListItemVO;
import com.studypilot.vo.QuizQuestionVO;
import com.studypilot.vo.QuizResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测验数据与所有权层：持久化、归属校验、VO 组装。
 * 被 QuizService / QuizGenerator / QuizScoringService 共享，避免循环依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizStoreService {

    private final QuizMapper quizMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    public Quiz getOwned(Long id) {
        Quiz quiz = quizMapper.selectById(id);
        if (quiz == null || !quiz.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测验不存在");
        }
        return quiz;
    }

    public Quiz createShell(QuizGenerateDTO dto, int questionCount, int difficulty) {
        Long userId = UserContext.getUserId();
        KnowledgeBase kb = knowledgeBaseMapper.selectById(dto.getKbId());
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在");
        }
        List<Integer> types = dto.getQuestionTypes().stream().distinct().sorted().toList();
        Quiz quiz = new Quiz();
        quiz.setUserId(userId);
        quiz.setKbId(dto.getKbId());
        quiz.setDocId(0L);
        quiz.setTopic(dto.getTopic() == null ? "" : dto.getTopic().trim());
        quiz.setTitle(dto.getTitle() == null || dto.getTitle().isBlank()
                ? defaultTitle(kb.getName(), quiz.getTopic()) : dto.getTitle().trim());
        quiz.setDifficulty(difficulty);
        quiz.setQuestionTypes(types.stream().map(String::valueOf).collect(Collectors.joining(",")));
        quiz.setQuestionCount(questionCount);
        quiz.setStatus(0);
        quiz.setGenerateStatus(GenerateStatus.NONE.getCode());
        quiz.setGenerateError("");
        quiz.setScore(0);
        quizMapper.insert(quiz);
        return quiz;
    }

    public void setGenerating(Long quizId) {
        quizMapper.update(null, new LambdaUpdateWrapper<Quiz>()
                .eq(Quiz::getId, quizId)
                .set(Quiz::getGenerateStatus, GenerateStatus.GENERATING.getCode())
                .set(Quiz::getGenerateError, ""));
    }

    @Transactional
    public void replaceQuestions(Long quizId, List<QuizQuestion> questions) {
        quizQuestionMapper.delete(new LambdaQueryWrapper<QuizQuestion>().eq(QuizQuestion::getQuizId, quizId));
        for (QuizQuestion q : questions) {
            q.setQuizId(quizId);
            quizQuestionMapper.insert(q);
        }
        quizMapper.update(null, new LambdaUpdateWrapper<Quiz>()
                .eq(Quiz::getId, quizId)
                .set(Quiz::getGenerateStatus, GenerateStatus.SUCCESS.getCode())
                .set(Quiz::getGenerateError, "")
                .set(Quiz::getQuestionCount, questions.size()));
    }

    public void markFailed(Long quizId, String message) {
        quizMapper.update(null, new LambdaUpdateWrapper<Quiz>()
                .eq(Quiz::getId, quizId)
                .set(Quiz::getGenerateStatus, GenerateStatus.FAILED.getCode())
                .set(Quiz::getGenerateError, message));
    }

    public List<QuizQuestion> questions(Long quizId) {
        return quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quizId)
                .orderByAsc(QuizQuestion::getSortOrder)
                .orderByAsc(QuizQuestion::getId));
    }

    public List<Quiz> listOwned() {
        return quizMapper.selectList(new LambdaQueryWrapper<Quiz>()
                .eq(Quiz::getUserId, UserContext.getUserId())
                .orderByDesc(Quiz::getId));
    }

    @Transactional
    public void delete(Long quizId) {
        quizQuestionMapper.delete(new LambdaQueryWrapper<QuizQuestion>().eq(QuizQuestion::getQuizId, quizId));
        quizMapper.deleteById(quizId);
    }

    public String kbName(Long kbId) {
        if (kbId == null || kbId == 0) {
            return "";
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        return kb == null ? "" : kb.getName();
    }

    public QuizListItemVO toListItem(Quiz quiz) {
        QuizListItemVO vo = new QuizListItemVO();
        vo.setId(quiz.getId());
        vo.setTitle(quiz.getTitle());
        vo.setTopic(quiz.getTopic());
        vo.setKbId(quiz.getKbId());
        vo.setKbName(kbName(quiz.getKbId()));
        vo.setDifficulty(quiz.getDifficulty());
        vo.setQuestionCount(quiz.getQuestionCount());
        vo.setStatus(quiz.getStatus());
        vo.setGenerateStatus(GenerateStatus.fromCode(quiz.getGenerateStatus()).getExternalName());
        vo.setScore(quiz.getScore());
        vo.setCreateTime(quiz.getCreateTime());
        return vo;
    }

    /** 练习态详情：隐藏答案/解析/来源原文/判分 */
    public QuizDetailVO toDetail(Quiz quiz) {
        QuizDetailVO vo = new QuizDetailVO();
        vo.setId(quiz.getId());
        vo.setTitle(quiz.getTitle());
        vo.setTopic(quiz.getTopic());
        vo.setKbId(quiz.getKbId());
        vo.setKbName(kbName(quiz.getKbId()));
        vo.setDifficulty(quiz.getDifficulty());
        vo.setQuestionTypes(parseTypes(quiz.getQuestionTypes()));
        vo.setQuestionCount(quiz.getQuestionCount());
        vo.setStatus(quiz.getStatus());
        vo.setGenerateStatus(GenerateStatus.fromCode(quiz.getGenerateStatus()).getExternalName());
        vo.setGenerateError(quiz.getGenerateError());
        vo.setScore(quiz.getScore());
        vo.setCreateTime(quiz.getCreateTime());
        List<QuizQuestionVO> list = new ArrayList<>();
        if (GenerateStatus.fromCode(quiz.getGenerateStatus()) == GenerateStatus.SUCCESS) {
            Map<Long, String> titles = docTitles(questions(quiz.getId()));
            for (QuizQuestion q : questions(quiz.getId())) {
                list.add(toQuestionVO(q, false, titles));
            }
        }
        vo.setQuestions(list);
        return vo;
    }

    public QuizResultVO toResult(Quiz quiz) {
        List<QuizQuestion> questions = questions(quiz.getId());
        Map<Long, String> titles = docTitles(questions);
        QuizResultVO vo = new QuizResultVO();
        vo.setQuizId(quiz.getId());
        vo.setTitle(quiz.getTitle());
        vo.setScore(quiz.getScore());
        vo.setQuestionCount(questions.size());
        vo.setCorrectCount((int) questions.stream().filter(q -> q.getIsCorrect() != null && q.getIsCorrect() == 1).count());
        vo.setStatus(quiz.getStatus());
        vo.setQuestions(questions.stream().map(q -> toQuestionVO(q, true, titles)).toList());
        return vo;
    }

    public QuizQuestionVO toQuestionVO(QuizQuestion q, boolean full, Map<Long, String> titles) {
        QuizQuestionVO vo = new QuizQuestionVO();
        vo.setId(q.getId());
        vo.setType(q.getType());
        vo.setDifficulty(q.getDifficulty());
        vo.setStem(q.getStem());
        vo.setOptions(parseOptions(q.getOptions()));
        vo.setKnowledgePoint(q.getKnowledgePoint());
        vo.setSourceDocId(q.getSourceDocId());
        vo.setSourceDocTitle(titles.getOrDefault(q.getSourceDocId(), ""));
        vo.setSortOrder(q.getSortOrder());
        if (full) {
            vo.setSourceContent(q.getSourceContent());
            vo.setAnswer(q.getAnswer());
            vo.setAnalysis(q.getAnalysis());
            vo.setUserAnswer(q.getUserAnswer());
            vo.setIsCorrect(q.getIsCorrect());
            vo.setScore(q.getScore());
            vo.setScoreBasis(q.getScoreBasis());
        }
        return vo;
    }

    public Map<Long, String> docTitles(List<QuizQuestion> questions) {
        List<Long> ids = questions.stream().map(QuizQuestion::getSourceDocId)
                .filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, String> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Document doc : documentMapper.selectBatchIds(ids)) {
                map.put(doc.getId(), doc.getTitle());
            }
        }
        return map;
    }

    public List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("选项 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Integer> parseTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::valueOf).toList();
    }

    private String defaultTitle(String kbName, String topic) {
        String base = topic == null || topic.isBlank() ? kbName : topic;
        String title = base + " 智能测验";
        return title.length() <= 64 ? title : title.substring(0, 64);
    }
}