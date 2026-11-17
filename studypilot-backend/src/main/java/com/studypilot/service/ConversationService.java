package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.MessageStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.config.RagProperties;
import com.studypilot.dto.CreateConversationDTO;
import com.studypilot.entity.ChatMessage;
import com.studypilot.entity.Conversation;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.ChatMessageMapper;
import com.studypilot.mapper.ConversationMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.ChatMessageVO;
import com.studypilot.vo.CitationVO;
import com.studypilot.vo.ConversationVO;
import com.studypilot.vo.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话与聊天历史模块：会话 CRUD、消息持久化、终态收敛。
 * 所有读写均绑定当前用户；引用 JSON 仅由后端组装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    public static final String DEFAULT_TITLE = "新会话";
    private static final int ROLE_USER = 0;
    private static final int ROLE_ASSISTANT = 1;

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatStreamRegistry chatStreamRegistry;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public ConversationVO create(CreateConversationDTO dto) {
        Long userId = UserContext.getUserId();
        long kbId = dto.getKbId();
        if (kbId != 0) {
            knowledgeBaseService.getOwnedKb(kbId);
        }
        String title = dto.getTitle() == null ? "" : dto.getTitle().strip();
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setKbId(kbId);
        conversation.setTitle(title.isEmpty() ? DEFAULT_TITLE : title);
        conversationMapper.insert(conversation);
        return toVO(conversation, kbNameMap());
    }

    public List<ConversationVO> list() {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, UserContext.getUserId())
                        .orderByDesc(Conversation::getUpdateTime));
        Map<Long, String> kbNames = kbNameMap();
        return conversations.stream().map(c -> toVO(c, kbNames)).toList();
    }

    public List<ChatMessageVO> messages(Long conversationId) {
        getOwned(conversationId);
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getId));
        List<ChatMessageVO> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            // 残留 GENERATING（服务重启/任务丢失）且无活跃生成 → 收敛为 STOPPED，保证终态一致
            if (MessageStatus.fromCode(message.getStatus()) == MessageStatus.GENERATING
                    && !chatStreamRegistry.isActive(message.getId())) {
                chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getId, message.getId())
                        .eq(ChatMessage::getStatus, MessageStatus.GENERATING.getCode())
                        .set(ChatMessage::getStatus, MessageStatus.STOPPED.getCode())
                        .set(ChatMessage::getErrorMsg, "连接中断，生成已停止"));
                message.setStatus(MessageStatus.STOPPED.getCode());
                message.setErrorMsg("连接中断，生成已停止");
            }
            result.add(toVO(message));
        }
        return result;
    }

    public void delete(Long conversationId) {
        getOwned(conversationId);
        if (chatStreamRegistry.hasActiveInConversation(conversationId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "会话正在生成回答，请等待完成或停止后再删除");
        }
        conversationMapper.deleteById(conversationId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
    }

    /** 校验归属，不存在或非本人一律 404 */
    public Conversation getOwned(Long id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null || !conversation.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    public ChatMessage saveUserMessage(Long conversationId, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(ROLE_USER);
        message.setContent(content);
        message.setTokenCount(0);
        message.setStatus(MessageStatus.SUCCESS.getCode());
        message.setErrorMsg("");
        chatMessageMapper.insert(message);
        touchConversation(conversationId);
        return message;
    }

    public ChatMessage createAssistantPlaceholder(Long conversationId, List<CitationVO> citations) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(ROLE_ASSISTANT);
        message.setContent("");
        message.setCitations(writeCitations(citations));
        message.setTokenCount(0);
        message.setStatus(MessageStatus.GENERATING.getCode());
        message.setErrorMsg("");
        chatMessageMapper.insert(message);
        return message;
    }

    public ChatMessageVO finalizeAssistant(Long messageId, String content, int tokenCount,
                                           MessageStatus status, String errorMsg) {
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getContent, content == null ? "" : content)
                .set(ChatMessage::getTokenCount, tokenCount)
                .set(ChatMessage::getStatus, status.getCode())
                .set(ChatMessage::getErrorMsg, errorMsg == null ? "" : errorMsg));
        touchConversation(messageConversationId(messageId));
        return toVO(chatMessageMapper.selectById(messageId));
    }

    /** 首问自动命名会话 */
    public void autoTitle(Conversation conversation, String question) {
        if (!DEFAULT_TITLE.equals(conversation.getTitle())) {
            return;
        }
        String title = question.length() > 30 ? question.substring(0, 30) + "…" : question;
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversation.getId())
                .set(Conversation::getTitle, title));
        conversation.setTitle(title);
    }

    /** 多轮上下文：仅取成功消息，条数 + 字符双重预算，排除当前占位消息 */
    public List<ChatMessage> historyForPrompt(Long conversationId, Long excludeMessageId) {
        List<ChatMessage> recent = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .ne(ChatMessage::getId, excludeMessageId)
                        .eq(ChatMessage::getStatus, MessageStatus.SUCCESS.getCode())
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + Math.max(1, ragProperties.getMaxHistoryMessages())));
        Collections.reverse(recent);
        List<ChatMessage> kept = new ArrayList<>();
        int chars = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage m = recent.get(i);
            int len = m.getContent() == null ? 0 : m.getContent().length();
            if (chars + len > ragProperties.getMaxHistoryChars() && !kept.isEmpty()) {
                break;
            }
            kept.add(m);
            chars += len;
        }
        Collections.reverse(kept);
        return kept;
    }

    public ChatMessageVO toVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole() != null && message.getRole() == ROLE_ASSISTANT ? "assistant" : "user");
        vo.setContent(message.getContent());
        vo.setCitations(readCitations(message.getCitations()));
        vo.setTokenCount(message.getTokenCount());
        vo.setStatus(MessageStatus.fromCode(message.getStatus()).name());
        vo.setErrorMsg(message.getErrorMsg());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    public String writeCitations(List<CitationVO> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            log.warn("引用序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<CitationVO> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationVO>>() {});
        } catch (Exception e) {
            log.warn("引用反序列化失败（按空引用处理）: {}", e.getMessage());
            return List.of();
        }
    }

    private void touchConversation(Long conversationId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    private Long messageConversationId(Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        return message == null ? null : message.getConversationId();
    }

    private Map<Long, String> kbNameMap() {
        return knowledgeBaseService.list().stream()
                .collect(Collectors.toMap(KnowledgeBaseVO::getId, KnowledgeBaseVO::getName, (a, b) -> a));
    }

    private ConversationVO toVO(Conversation conversation, Map<Long, String> kbNames) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setKbId(conversation.getKbId());
        vo.setKbName(conversation.getKbId() == 0 ? "未分配文档"
                : kbNames.getOrDefault(conversation.getKbId(), "知识库已删除"));
        vo.setTitle(conversation.getTitle());
        vo.setCreateTime(conversation.getCreateTime());
        vo.setUpdateTime(conversation.getUpdateTime());
        return vo;
    }
}