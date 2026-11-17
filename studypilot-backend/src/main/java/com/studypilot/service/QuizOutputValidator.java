package com.studypilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.ResultCode;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.exception.BusinessException;
import com.studypilot.vo.QuizGenerateResult;
import com.studypilot.vo.VectorSearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 出题结果校验器：不直接信任大模型输出。
 * 校验题目数量、题型、各题型答案合法性、引用片段真实性，全部通过才映射为可持久化题目；
 * 任一不合法即整体失败，绝不把无法解析/不合规的输出保存为有效题目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizOutputValidator {

    private final ObjectMapper objectMapper;

    public List<QuizQuestion> validateAndMap(QuizGenerateResult result, int expectedCount,
                                             List<Integer> allowedTypes, int difficulty,
                                             List<VectorSearchResultVO> chunks) {
        List<QuizGenerateResult.QuestionItem> items = result.getQuestions();
        if (items == null || items.isEmpty()) {
            throw fail("AI 未生成任何题目，请重试");
        }
        if (items.size() != expectedCount) {
            throw fail("AI 生成题目数量不符（期望 " + expectedCount + "，实际 " + items.size() + "），请重试");
        }
        if (chunks == null || chunks.isEmpty()) {
            throw fail("缺少出题依据的知识库内容，请重试");
        }
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            QuizGenerateResult.QuestionItem item = items.get(i);
            String label = "第" + (i + 1) + "题";
            if (item == null) {
                throw fail(label + "为空，请重试");
            }
            Integer type = item.getType();
            if (type == null || type < 1 || type > 4 || !allowedTypes.contains(type)) {
                throw fail(label + "题型非法或不在所选范围内，请重试");
            }
            if (isBlank(item.getStem())) {
                throw fail(label + "题干为空，请重试");
            }
            if (isBlank(item.getAnalysis())) {
                throw fail(label + "缺少题目解析，请重试");
            }
            List<String> options = item.getOptions() == null ? List.of() : item.getOptions();
            String answer = item.getAnswer() == null ? "" : item.getAnswer().trim();
            validateAnswer(type, options, answer, label);

            Integer sourceIndex = item.getSourceIndex();
            if (sourceIndex == null || sourceIndex < 0 || sourceIndex >= chunks.size()) {
                throw fail(label + "引用来源序号非法（涉嫌伪造来源），请重试");
            }
            VectorSearchResultVO source = chunks.get(sourceIndex);

            QuizQuestion q = new QuizQuestion();
            q.setType(type);
            q.setDifficulty(difficulty);
            q.setStem(item.getStem().trim());
            q.setOptions(writeOptions(type, options));
            q.setAnswer(normalizeAnswer(type, answer));
            q.setAnalysis(item.getAnalysis().trim());
            q.setKnowledgePoint(item.getKnowledgePoint() == null ? "" : item.getKnowledgePoint().trim());
            q.setSourceChunkId(source.getChunkId());
            q.setSourceDocId(source.getDocumentId());
            q.setSourceContent(source.getContent());
            q.setUserAnswer("");
            q.setIsCorrect(-1);
            q.setScore(0);
            q.setScoreBasis("");
            q.setSortOrder(i + 1);
            questions.add(q);
        }
        return questions;
    }

    private void validateAnswer(int type, List<String> options, String answer, String label) {
        switch (type) {
            case 1 -> {
                if (options.size() < 2 || options.size() > 6) {
                    throw fail(label + "单选选项数量非法，请重试");
                }
                if (!answer.matches("^[A-Z]$") || letterIndex(answer.charAt(0)) >= options.size()) {
                    throw fail(label + "单选答案非法，请重试");
                }
            }
            case 2 -> {
                if (options.size() < 2 || options.size() > 6) {
                    throw fail(label + "多选选项数量非法，请重试");
                }
                Set<Integer> picked = parseLetters(answer, options.size(), label);
                if (picked.size() < 2) {
                    throw fail(label + "多选答案至少包含两个选项，请重试");
                }
            }
            case 3 -> {
                if (options.size() != 2) {
                    throw fail(label + "判断题选项必须为两项，请重试");
                }
                if (!answer.equals("A") && !answer.equals("B")) {
                    throw fail(label + "判断题答案非法，请重试");
                }
            }
            case 4 -> {
                if (isBlank(answer)) {
                    throw fail(label + "简答题缺少参考答案，请重试");
                }
            }
            default -> throw fail(label + "题型非法，请重试");
        }
    }

    private Set<Integer> parseLetters(String answer, int optionCount, String label) {
        if (isBlank(answer)) {
            throw fail(label + "答案为空，请重试");
        }
        Set<Integer> picked = new LinkedHashSet<>();
        for (String part : answer.split(",")) {
            String token = part.trim().toUpperCase();
            if (!token.matches("^[A-Z]$")) {
                throw fail(label + "答案包含非法选项标识，请重试");
            }
            int idx = letterIndex(token.charAt(0));
            if (idx >= optionCount) {
                throw fail(label + "答案选项超出范围，请重试");
            }
            picked.add(idx);
        }
        return picked;
    }

    /** 多选答案规范化为按字母升序、逗号分隔，便于严格全对判分 */
    private String normalizeAnswer(int type, String answer) {
        if (type == 2) {
            Set<Integer> picked = parseLetters(answer, 26, "多选");
            List<Integer> sorted = new ArrayList<>(picked);
            sorted.sort(Integer::compareTo);
            StringBuilder sb = new StringBuilder();
            for (int idx : sorted) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append((char) ('A' + idx));
            }
            return sb.toString();
        }
        if (type == 1 || type == 3) {
            return answer.toUpperCase();
        }
        return answer;
    }

    private String writeOptions(int type, List<String> options) {
        try {
            if (type == 4) {
                return "[]";
            }
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw fail("选项序列化失败，请重试");
        }
    }

    private static int letterIndex(char c) {
        return c - 'A';
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BusinessException fail(String message) {
        return new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
    }
}