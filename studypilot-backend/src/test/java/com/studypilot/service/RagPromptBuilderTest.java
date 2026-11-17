package com.studypilot.service;

import com.studypilot.config.RagProperties;
import com.studypilot.entity.ChatMessage;
import com.studypilot.vo.CitationVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPromptBuilderTest {

    private RagPromptBuilder builder(RagProperties props) {
        return new RagPromptBuilder(props);
    }

    private CitationVO citation(long chunkId, String doc, String content) {
        CitationVO c = new CitationVO();
        c.setDocumentId(1L);
        c.setDocumentName(doc);
        c.setChunkId(chunkId);
        c.setPageNum(2);
        c.setOriginalContent(content);
        c.setSimilarityScore(0.7);
        return c;
    }

    private ChatMessage message(int role, String content) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void systemPromptContainsAntiFabricationRules() {
        RagProperties props = new RagProperties();
        List<Map<String, String>> messages = builder(props).build("问题",
                List.of(citation(1, "论文A", "内容")), List.of());
        String system = messages.get(0).get("content");
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(system.contains("优先依据"));
        assertTrue(system.contains("猜测"));
        assertTrue(system.contains("未找到相关信息"));
        assertTrue(system.contains("不得伪造论文标题、作者、页码和引用"));
        assertTrue(system.contains("冲突"));
    }

    @Test
    void citationsNumberedInOrder() {
        RagProperties props = new RagProperties();
        List<Map<String, String>> messages = builder(props).build("什么是联邦学习？",
                List.of(citation(11, "论文A", "内容甲"), citation(22, "论文B", "内容乙")), List.of());
        String user = messages.get(messages.size() - 1).get("content");
        assertTrue(user.contains("[来源1]"));
        assertTrue(user.contains("[来源2]"));
        assertTrue(user.contains("chunkId=11"));
        assertTrue(user.contains("chunkId=22"));
        assertTrue(user.contains("《论文A》"));
        assertTrue(user.contains("第2页"));
        assertTrue(user.contains("【用户问题】\n什么是联邦学习？"));
    }

    @Test
    void historyRolesMappedAndOrdered() {
        RagProperties props = new RagProperties();
        List<Map<String, String>> messages = builder(props).build("追问",
                List.of(citation(1, "论文A", "内容")),
                List.of(message(0, "首问"), message(1, "首答")));
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(1).get("role"));
        assertEquals("首问", messages.get(1).get("content"));
        assertEquals("assistant", messages.get(2).get("role"));
        assertEquals("首答", messages.get(2).get("content"));
    }

    @Test
    void contextBudgetTruncates() {
        RagProperties props = new RagProperties();
        props.setMaxContextChars(120);
        String longContent = "长".repeat(500);
        List<Map<String, String>> messages = builder(props).build("问题",
                List.of(citation(1, "论文A", longContent), citation(2, "论文B", "另一段")), List.of());
        String user = messages.get(messages.size() - 1).get("content");
        assertTrue(user.contains("…(内容截断)"));
        assertTrue(user.length() < 400, "预算应限制上下文总长: " + user.length());
    }

    @Test
    void emptyHistoryProducesSystemAndUserOnly() {
        RagProperties props = new RagProperties();
        List<Map<String, String>> messages = builder(props).build("问题",
                List.of(citation(1, "论文A", "内容")), List.of());
        assertEquals(2, messages.size());
    }
}