package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "studypilot.rag")
public class RagProperties {

    /** 默认检索 Top-K */
    private int topK = 5;

    /** 默认最低相似度阈值，低于该分数的切片不进入上下文 */
    private double minScore = 0.5;

    /** 单次提问最大字符数 */
    private int maxQuestionChars = 2000;

    /** 多轮上下文最多携带的历史消息条数 */
    private int maxHistoryMessages = 10;

    /** 历史消息总字符预算（超出从最早开始丢弃） */
    private int maxHistoryChars = 4000;

    /** 检索内容进入 Prompt 的总字符预算 */
    private int maxContextChars = 8000;

    /** SseEmitter 整体超时 */
    private long sseTimeoutMs = 180000;

    /** 单次生成硬 deadline，防止后台任务无限运行 */
    private long maxGenerationMs = 180000;

    /** 流式读取空闲超时（两个数据块之间） */
    private int streamIdleTimeoutMs = 60000;
}