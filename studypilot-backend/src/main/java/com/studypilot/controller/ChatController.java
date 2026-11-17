package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.CreateConversationDTO;
import com.studypilot.dto.SendMessageDTO;
import com.studypilot.service.ChatStreamService;
import com.studypilot.service.ConversationService;
import com.studypilot.vo.ChatMessageVO;
import com.studypilot.vo.ConversationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ConversationService conversationService;
    private final ChatStreamService chatStreamService;

    @PostMapping("/api/chat/sessions")
    public Result<ConversationVO> create(@Valid @RequestBody CreateConversationDTO dto) {
        return Result.success(conversationService.create(dto));
    }

    @GetMapping("/api/chat/sessions")
    public Result<List<ConversationVO>> list() {
        return Result.success(conversationService.list());
    }

    @GetMapping("/api/chat/sessions/{id}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return Result.success(conversationService.messages(id));
    }

    @DeleteMapping("/api/chat/sessions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.success();
    }

    /** 发送问题并以 SSE 流式返回（meta/delta/done/error）；JWT 走 Authorization 头 */
    @PostMapping(value = "/api/chat/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@PathVariable Long id, @Valid @RequestBody SendMessageDTO dto) {
        return chatStreamService.send(id, dto);
    }

    /** 用户主动停止当前会话的生成（不依赖客户端 TCP 断开检测），落 STOPPED 终态 */
    @PostMapping("/api/chat/sessions/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        chatStreamService.cancel(id);
        return Result.success();
    }
}