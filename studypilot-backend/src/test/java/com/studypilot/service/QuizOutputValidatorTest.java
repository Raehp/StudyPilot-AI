package com.studypilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.exception.BusinessException;
import com.studypilot.vo.QuizGenerateResult;
import com.studypilot.vo.VectorSearchResultVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizOutputValidatorTest {

    private final QuizOutputValidator validator = new QuizOutputValidator(new ObjectMapper());

    private VectorSearchResultVO chunk(long chunkId, long docId, String content) {
        VectorSearchResultVO c = new VectorSearchResultVO();
        c.setChunkId(chunkId);
        c.setDocumentId(docId);
        c.setDocumentTitle("论文A");
        c.setContent(content);
        c.setPageNum(2);
        c.setScore(0.7);
        return c;
    }

    private QuizGenerateResult.QuestionItem item(int type, List<String> options, String answer, Integer sourceIndex) {
        QuizGenerateResult.QuestionItem q = new QuizGenerateResult.QuestionItem();
        q.setType(type);
        q.setStem("题干");
        q.setOptions(options);
        q.setAnswer(answer);
        q.setAnalysis("解析");
        q.setKnowledgePoint("知识点");
        q.setSourceIndex(sourceIndex);
        return q;
    }

    private QuizGenerateResult result(QuizGenerateResult.QuestionItem... items) {
        QuizGenerateResult r = new QuizGenerateResult();
        r.setQuestions(new ArrayList<>(List.of(items)));
        return r;
    }

    private List<VectorSearchResultVO> chunks() {
        return List.of(chunk(101, 11, "原文甲"), chunk(102, 12, "原文乙"));
    }

    @Test
    void validAllTypesMappedWithRealSource() {
        QuizGenerateResult r = result(
                item(1, List.of("甲", "乙", "丙", "丁"), "B", 0),
                item(2, List.of("甲", "乙", "丙", "丁"), "C,A", 1),
                item(3, List.of("正确", "错误"), "A", 0),
                item(4, List.of(), "参考答案要点", 1));
        List<QuizQuestion> qs = validator.validateAndMap(r, 4, List.of(1, 2, 3, 4), 2, chunks());
        assertEquals(4, qs.size());
        assertEquals(101L, qs.get(0).getSourceChunkId());
        assertEquals(11L, qs.get(0).getSourceDocId());
        assertEquals("原文甲", qs.get(0).getSourceContent());
        assertEquals("A,C", qs.get(1).getAnswer(), "多选答案应规范化为升序");
        assertEquals(102L, qs.get(1).getSourceChunkId());
        assertEquals(2, qs.get(0).getDifficulty());
        assertEquals(-1, qs.get(0).getIsCorrect());
        assertEquals(1, qs.get(0).getSortOrder());
        assertEquals(4, qs.get(3).getSortOrder());
    }

    @Test
    void countMismatchFails() {
        QuizGenerateResult r = result(item(1, List.of("甲", "乙", "丙", "丁"), "A", 0));
        BusinessException e = assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 3, List.of(1), 1, chunks()));
        assertTrue(e.getMessage().contains("数量不符"));
    }

    @Test
    void emptyQuestionsFails() {
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(result(), 1, List.of(1), 1, chunks()));
    }

    @Test
    void singleAnswerOutOfRangeFails() {
        QuizGenerateResult r = result(item(1, List.of("甲", "乙"), "C", 0));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(1), 1, chunks()));
    }

    @Test
    void multiAnswerLessThanTwoFails() {
        QuizGenerateResult r = result(item(2, List.of("甲", "乙", "丙", "丁"), "A", 0));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(2), 1, chunks()));
    }

    @Test
    void judgeOptionsMustBeTwo() {
        QuizGenerateResult r = result(item(3, List.of("正确", "错误", "不确定"), "A", 0));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(3), 1, chunks()));
    }

    @Test
    void shortAnswerBlankFails() {
        QuizGenerateResult r = result(item(4, List.of(), "  ", 0));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(4), 1, chunks()));
    }

    @Test
    void forgedSourceIndexFails() {
        QuizGenerateResult r = result(item(1, List.of("甲", "乙", "丙", "丁"), "A", 9));
        BusinessException e = assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(1), 1, chunks()));
        assertTrue(e.getMessage().contains("伪造来源"));
    }

    @Test
    void nullSourceIndexFails() {
        QuizGenerateResult r = result(item(1, List.of("甲", "乙", "丙", "丁"), "A", null));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(1), 1, chunks()));
    }

    @Test
    void typeNotInAllowedRangeFails() {
        QuizGenerateResult r = result(item(4, List.of(), "答案", 0));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(1, 3), 1, chunks()));
    }

    @Test
    void blankStemFails() {
        QuizGenerateResult.QuestionItem q = item(1, List.of("甲", "乙", "丙", "丁"), "A", 0);
        q.setStem("  ");
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(result(q), 1, List.of(1), 1, chunks()));
    }

    @Test
    void emptyChunksFails() {
        QuizGenerateResult r = result(item(1, List.of("甲", "乙", "丙", "丁"), "A", 0));
        assertThrows(BusinessException.class,
                () -> validator.validateAndMap(r, 1, List.of(1), 1, List.of()));
    }
}