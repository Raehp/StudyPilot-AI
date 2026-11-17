package com.studypilot.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 活跃生成注册表：assistantMessageId → 生成任务。
 * 用于客户端断开时取消生成、防止同会话并行生成、
 * 以及历史加载时收敛残留 GENERATING 消息（如服务重启后）。
 */
@Component
public class ChatStreamRegistry {

    public static class ActiveGeneration {
        private final Long conversationId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        ActiveGeneration(Long conversationId) {
            this.conversationId = conversationId;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            cancelled.set(true);
        }
    }

    private final Map<Long, ActiveGeneration> active = new ConcurrentHashMap<>();

    public ActiveGeneration register(Long assistantMessageId, Long conversationId) {
        ActiveGeneration generation = new ActiveGeneration(conversationId);
        active.put(assistantMessageId, generation);
        return generation;
    }

    public void remove(Long assistantMessageId) {
        active.remove(assistantMessageId);
    }

    public boolean isActive(Long assistantMessageId) {
        return active.containsKey(assistantMessageId);
    }

    public boolean hasActiveInConversation(Long conversationId) {
        return active.values().stream()
                .anyMatch(g -> g.getConversationId().equals(conversationId));
    }

    /** 用户主动停止：取消该会话当前活跃生成（不依赖 TCP 断开检测），返回是否命中 */
    public boolean cancelByConversation(Long conversationId) {
        boolean found = false;
        for (ActiveGeneration generation : active.values()) {
            if (generation.getConversationId().equals(conversationId)) {
                generation.cancel();
                found = true;
            }
        }
        return found;
    }
}