package com.studypilot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.config.RagProperties;
import com.studypilot.entity.ChatMessage;
import com.studypilot.mapper.ChatMessageMapper;
import com.studypilot.mapper.ConversationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationHistoryTest {

    @BeforeAll
    static void initEntityMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChatMessage.class);
    }

    private ConversationService service(ChatMessageMapper mapper, RagProperties props) {
        return new ConversationService(mock(ConversationMapper.class), mapper,
                mock(KnowledgeBaseService.class), new ChatStreamRegistry(), new ObjectMapper(), props);
    }

    private ChatMessage msg(long id, int chars) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setRole((int) (id % 2));
        m.setContent("x".repeat(chars));
        m.setStatus(1);
        return m;
    }

    @Test
    void historyCappedByMessageCount() {
        RagProperties props = new RagProperties();
        props.setMaxHistoryMessages(10);
        props.setMaxHistoryChars(100000);
        ChatMessageMapper mapper = mock(ChatMessageMapper.class);
        // 条数上限由 SQL LIMIT 强制，mock 模拟数据库仅返回 10 条（倒序）
        List<ChatMessage> desc = new ArrayList<>();
        for (long i = 15; i >= 6; i--) {
            desc.add(msg(i, 10));
        }
        when(mapper.selectList(any())).thenReturn(desc);
        List<ChatMessage> kept = service(mapper, props).historyForPrompt(1L, 99L);
        assertEquals(10, kept.size());
        assertEquals(6L, kept.get(0).getId());
        assertEquals(15L, kept.get(9).getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ChatMessage>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("LIMIT 10"));
    }

    @Test
    void historyCappedByCharBudgetFromNewest() {
        RagProperties props = new RagProperties();
        props.setMaxHistoryMessages(10);
        props.setMaxHistoryChars(4000);
        ChatMessageMapper mapper = mock(ChatMessageMapper.class);
        List<ChatMessage> desc = new ArrayList<>();
        for (long i = 12; i >= 1; i--) {
            desc.add(msg(i, 500));
        }
        when(mapper.selectList(any())).thenReturn(desc);
        List<ChatMessage> kept = service(mapper, props).historyForPrompt(1L, 99L);
        assertEquals(8, kept.size());
        assertEquals(5L, kept.get(0).getId());
        assertEquals(12L, kept.get(7).getId());
    }

    @Test
    void singleOversizedMessageStillKept() {
        RagProperties props = new RagProperties();
        props.setMaxHistoryMessages(10);
        props.setMaxHistoryChars(100);
        ChatMessageMapper mapper = mock(ChatMessageMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(msg(1, 500)));
        List<ChatMessage> kept = service(mapper, props).historyForPrompt(1L, 99L);
        assertEquals(1, kept.size());
    }
}