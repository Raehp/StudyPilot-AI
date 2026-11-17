package com.studypilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.MessageStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.config.RagProperties;
import com.studypilot.dto.SendMessageDTO;
import com.studypilot.entity.ChatMessage;
import com.studypilot.entity.Conversation;
import com.studypilot.exception.BusinessException;
import com.studypilot.vo.ChatMessageVO;
import com.studypilot.vo.CitationVO;
import com.studypilot.vo.VectorSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * RAG 问答编排：校验 → 检索 → 保存用户消息 → 组装引用 → 流式生成 → 终态落库。
 * 检索与校验在请求线程完成（失败以 JSON 业务错误返回）；
 * 生成在 chatExecutor 异步执行，SSE 事件 meta/delta/done/error；
 * 客户端断开/超时 → 取消生成 → STOPPED 终态，后台任务必然终止。
 */
@Slf4j
@Service
public class ChatStreamService {

    private final ConversationService conversationService;
    private final RagRetrievalService ragRetrievalService;
    private final RagPromptBuilder ragPromptBuilder;
    private final DashScopeStreamClient dashScopeStreamClient;
    private final ChatStreamRegistry chatStreamRegistry;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final Executor chatExecutor;

    public ChatStreamService(ConversationService conversationService,
                             RagRetrievalService ragRetrievalService,
                             RagPromptBuilder ragPromptBuilder,
                             DashScopeStreamClient dashScopeStreamClient,
                             ChatStreamRegistry chatStreamRegistry,
                             KnowledgeBaseService knowledgeBaseService,
                             RagProperties ragProperties,
                             ObjectMapper objectMapper,
                             @Qualifier("chatExecutor") Executor chatExecutor) {
        this.conversationService = conversationService;
        this.ragRetrievalService = ragRetrievalService;
        this.ragPromptBuilder = ragPromptBuilder;
        this.dashScopeStreamClient = dashScopeStreamClient;
        this.chatStreamRegistry = chatStreamRegistry;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.chatExecutor = chatExecutor;
    }

    public SseEmitter send(Long conversationId, SendMessageDTO dto) {
        Conversation conversation = conversationService.getOwned(conversationId);
        String question = dto.getContent() == null ? "" : dto.getContent().strip();
        if (question.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "问题不能为空");
        }
        if (question.length() > ragProperties.getMaxQuestionChars()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "问题过长（上限 " + ragProperties.getMaxQuestionChars() + " 字符）");
        }
        if (chatStreamRegistry.hasActiveInConversation(conversationId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "当前会话有正在生成的回答，请等待完成或停止后再发送");
        }
        // 检索失败（如 Embedding 异常）此时尚未保存任何消息，直接 JSON 业务错误
        List<VectorSearchResultVO> hits = ragRetrievalService.retrieve(
                conversation.getKbId(), question, dto.getTopK(), dto.getMinScore());
        ChatMessage userMessage = conversationService.saveUserMessage(conversationId, question);
        conversationService.autoTitle(conversation, question);
        List<CitationVO> citations = hits.stream().map(this::toCitation).toList();
        ChatMessage assistant = conversationService.createAssistantPlaceholder(conversationId, citations);

        SseEmitter emitter = new SseEmitter(ragProperties.getSseTimeoutMs());
        sendEvent(emitter, "meta", Map.of(
                "userMessageId", userMessage.getId(),
                "assistantMessageId", assistant.getId(),
                "retrievalCount", hits.size(),
                "citations", citations));

        if (hits.isEmpty()) {
            String text = noHitText(conversation, dto.getMinScore());
            ChatMessageVO vo = conversationService.finalizeAssistant(
                    assistant.getId(), text, 0, MessageStatus.SUCCESS, "");
            sendEvent(emitter, "delta", Map.of("content", text));
            sendEvent(emitter, "done", vo);
            emitter.complete();
            return emitter;
        }

        ChatStreamRegistry.ActiveGeneration generation =
                chatStreamRegistry.register(assistant.getId(), conversationId);
        emitter.onCompletion(() -> {
            generation.cancel();
            chatStreamRegistry.remove(assistant.getId());
        });
        emitter.onTimeout(() -> {
            generation.cancel();
            chatStreamRegistry.remove(assistant.getId());
            emitter.complete();
        });
        emitter.onError(e -> {
            generation.cancel();
            chatStreamRegistry.remove(assistant.getId());
        });

        List<ChatMessage> history = conversationService.historyForPrompt(conversationId, assistant.getId());
        List<Map<String, String>> prompt = ragPromptBuilder.build(question, citations, history);
        Long userId = conversation.getUserId();
        try {
            chatExecutor.execute(() -> runGeneration(emitter, generation, userId, assistant.getId(), prompt));
        } catch (TaskRejectedException e) {
            chatStreamRegistry.remove(assistant.getId());
            conversationService.finalizeAssistant(assistant.getId(), "", 0,
                    MessageStatus.FAILED, "AI 服务繁忙，请稍后重试");
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "AI 服务繁忙，请稍后重试");
        }
        return emitter;
    }

    /** 用户主动停止：校验归属后取消该会话活跃生成，runGeneration 感知取消标志落 STOPPED 终态 */
    public void cancel(Long conversationId) {
        conversationService.getOwned(conversationId);
        chatStreamRegistry.cancelByConversation(conversationId);
    }

    private void runGeneration(SseEmitter emitter, ChatStreamRegistry.ActiveGeneration generation,
                               Long userId, Long assistantMessageId, List<Map<String, String>> prompt) {
        try {
            DashScopeStreamClient.StreamResult result = dashScopeStreamClient.streamChat(
                    userId, prompt,
                    delta -> {
                        if (generation.isCancelled()) {
                            return;
                        }
                        if (!sendEvent(emitter, "delta", Map.of("content", delta))) {
                            generation.cancel();
                        }
                    },
                    generation::isCancelled);
            int tokens = result.promptTokens() + result.completionTokens();
            if (generation.isCancelled() || result.stopped()) {
                String reason = result.stopped() ? result.stopReason() : "客户端断开，生成已停止";
                conversationService.finalizeAssistant(assistantMessageId, result.content(), tokens,
                        MessageStatus.STOPPED, reason);
                safeComplete(emitter);
                return;
            }
            ChatMessageVO vo = conversationService.finalizeAssistant(
                    assistantMessageId, result.content(), tokens, MessageStatus.SUCCESS, "");
            sendEvent(emitter, "done", vo);
            emitter.complete();
        } catch (BusinessException e) {
            conversationService.finalizeAssistant(assistantMessageId, "", 0,
                    MessageStatus.FAILED, e.getMessage());
            sendEvent(emitter, "error", Map.of(
                    "assistantMessageId", assistantMessageId, "message", e.getMessage()));
            safeComplete(emitter);
        } catch (Exception e) {
            log.error("聊天生成异常 assistantMessageId={}", assistantMessageId, e);
            String message = "AI 服务异常，请稍后重试";
            conversationService.finalizeAssistant(assistantMessageId, "", 0, MessageStatus.FAILED, message);
            sendEvent(emitter, "error", Map.of(
                    "assistantMessageId", assistantMessageId, "message", message));
            safeComplete(emitter);
        } finally {
            chatStreamRegistry.remove(assistantMessageId);
        }
    }

    private CitationVO toCitation(VectorSearchResultVO hit) {
        CitationVO citation = new CitationVO();
        citation.setDocumentId(hit.getDocumentId());
        citation.setDocumentName(hit.getDocumentTitle());
        citation.setChunkId(hit.getChunkId());
        citation.setChunkUid(hit.getChunkUid());
        citation.setPageNum(hit.getPageNum());
        citation.setOriginalContent(hit.getContent());
        citation.setSimilarityScore(hit.getScore());
        return citation;
    }

    private String noHitText(Conversation conversation, Double minScoreOverride) {
        double threshold = minScoreOverride == null ? ragProperties.getMinScore()
                : Math.max(0.0, Math.min(minScoreOverride, 1.0));
        String kbName = conversation.getKbId() == 0 ? "未分配文档"
                : knowledgeBaseService.getOwnedKb(conversation.getKbId()).getName();
        return "未在知识库「" + kbName + "」中检索到与该问题相关的内容（最低相似度阈值 " + threshold
                + "）。请尝试更换知识库、调整问法，或先上传并解析相关文档。";
    }

    /** SSE 发送；客户端已断开时返回 false（不向用户抛出堆栈） */
    private boolean sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(name).data(json, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            log.debug("SSE 发送失败（客户端可能已断开）event={} cause={}", name, e.getMessage());
            return false;
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端已断开
        }
    }
}