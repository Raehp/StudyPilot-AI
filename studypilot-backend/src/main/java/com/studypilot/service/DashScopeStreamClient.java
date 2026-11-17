package com.studypilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.ResultCode;
import com.studypilot.config.DashScopeProperties;
import com.studypilot.config.RagProperties;
import com.studypilot.entity.LogAiCall;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.LogAiCallMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 通义千问流式对话客户端（DashScope OpenAI 兼容 /chat/completions，stream=true）。
 * 已用真实调用验证：text/event-stream 增量 + [DONE] + stream_options.include_usage。
 * 支持取消（客户端断开/停止）、空闲读超时、整体 deadline；
 * 失败脱敏映射，log_ai_call 落库（biz_type=3 RAG，不含密钥）。
 */
@Slf4j
@Service
public class DashScopeStreamClient {

    private static final int BIZ_TYPE_RAG = 3;

    public record StreamResult(String content, int promptTokens, int completionTokens,
                               boolean stopped, String stopReason) {
    }

    private final DashScopeProperties props;
    private final RagProperties ragProps;
    private final LogAiCallMapper logAiCallMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Semaphore semaphore;

    public DashScopeStreamClient(DashScopeProperties props, RagProperties ragProps,
                                 LogAiCallMapper logAiCallMapper, ObjectMapper objectMapper) {
        this.props = props;
        this.ragProps = ragProps;
        this.logAiCallMapper = logAiCallMapper;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        // 流式场景读超时作用于相邻数据块之间的空闲间隔
        factory.setReadTimeout(ragProps.getStreamIdleTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.semaphore = new Semaphore(Math.max(1, props.getMaxConcurrent()));
    }

    public StreamResult streamChat(Long userId, List<Map<String, String>> messages,
                                   Consumer<String> onDelta, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            return new StreamResult("", 0, 0, true, "客户端断开或用户停止");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                    "AI 服务未配置 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 调用被中断，请重试");
        }
        if (!acquired) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 服务繁忙，请稍后重试");
        }
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "messages", messages,
                    "stream", true,
                    "stream_options", Map.of("include_usage", true),
                    "temperature", 0.3);
            return restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int http = response.getStatusCode().value();
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            String errBody = readLimited(response.getBody(), 300);
                            long cost = System.currentTimeMillis() - start;
                            String message = mapHttpMessage(http);
                            recordCall(userId, 0, 0, cost, http == 429 ? 3 : 1, message);
                            log.warn("流式调用失败 model={} http={} latencyMs={} body={}",
                                    props.getModel(), http, cost, errBody);
                            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
                        }
                        return consumeStream(userId, response.getBody(), onDelta, cancelled, start);
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            long cost = System.currentTimeMillis() - start;
            boolean timeout = false;
            Integer retryHttpStatus = null;
            for (Throwable t = e; t != null; t = t.getCause()) {
                // JDK HttpURLConnection 对 401/407 在流式请求体下抛 HttpRetryException，需按真实状态码映射
                if (t instanceof java.net.HttpRetryException hre) {
                    retryHttpStatus = hre.responseCode();
                }
                if (t instanceof SocketTimeoutException) {
                    timeout = true;
                }
            }
            String message;
            int logStatus;
            if (retryHttpStatus != null) {
                message = mapHttpMessage(retryHttpStatus);
                logStatus = retryHttpStatus == 429 ? 3 : 1;
            } else {
                message = timeout ? "AI 服务响应超时，请稍后重试" : "AI 服务网络异常，请稍后重试";
                logStatus = timeout ? 2 : 1;
            }
            recordCall(userId, 0, 0, cost, logStatus, message);
            log.warn("流式调用网络异常 model={} latencyMs={} timeout={} retryHttpStatus={} cause={}",
                    props.getModel(), cost, timeout, retryHttpStatus, e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
        } finally {
            semaphore.release();
        }
    }

    private StreamResult consumeStream(Long userId, InputStream body, Consumer<String> onDelta,
                                       BooleanSupplier cancelled, long start) throws IOException {
        StringBuilder content = new StringBuilder();
        int promptTokens = 0;
        int completionTokens = 0;
        boolean stopped = false;
        String stopReason = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.getAsBoolean()) {
                    stopped = true;
                    stopReason = "客户端断开或用户停止";
                    break;
                }
                if (System.currentTimeMillis() - start > ragProps.getMaxGenerationMs()) {
                    stopped = true;
                    stopReason = "生成超时终止";
                    break;
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String payload = trimmed.substring(5).trim();
                if (payload.equals("[DONE]")) {
                    break;
                }
                if (payload.isEmpty()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = objectMapper.readTree(payload);
                } catch (Exception e) {
                    log.warn("流式数据块解析失败（跳过）: {}", e.getMessage());
                    continue;
                }
                JsonNode usage = node.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                }
                String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                if (!delta.isEmpty()) {
                    content.append(delta);
                    onDelta.accept(delta);
                }
            }
        } catch (IOException e) {
            long cost = System.currentTimeMillis() - start;
            boolean timeout = e instanceof SocketTimeoutException || hasTimeoutCause(e);
            String message = timeout ? "AI 服务响应超时，请稍后重试" : "AI 服务网络异常，请稍后重试";
            recordCall(userId, promptTokens, completionTokens, cost, timeout ? 2 : 1, message);
            log.warn("流式读取异常 model={} latencyMs={} timeout={} cause={}",
                    props.getModel(), cost, timeout, e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
        }
        long cost = System.currentTimeMillis() - start;
        if (stopped) {
            recordCall(userId, promptTokens, completionTokens, cost, 0, stopReason);
            log.info("流式生成停止 model={} reason={} chars={} latencyMs={}",
                    props.getModel(), stopReason, content.length(), cost);
            return new StreamResult(content.toString(), promptTokens, completionTokens, true, stopReason);
        }
        if (content.isEmpty()) {
            recordCall(userId, promptTokens, completionTokens, cost, 1, "模型返回空内容");
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 返回结果为空，请重试");
        }
        recordCall(userId, promptTokens, completionTokens, cost, 0, "");
        log.info("流式调用成功 model={} chars={} latencyMs={} promptTokens={} completionTokens={}",
                props.getModel(), content.length(), cost, promptTokens, completionTokens);
        return new StreamResult(content.toString(), promptTokens, completionTokens, false, "");
    }

    private String mapHttpMessage(int http) {
        if (http == 429) {
            return "AI 服务限流，请稍后重试";
        }
        if (http == 401 || http == 403) {
            return "AI 服务认证失败，请检查 API Key 配置";
        }
        if (http >= 500) {
            return "AI 服务暂时不可用，请稍后重试";
        }
        return "AI 请求被拒绝（HTTP " + http + "），请稍后重试";
    }

    private boolean hasTimeoutCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String readLimited(InputStream in, int max) {
        try {
            return new String(in.readNBytes(max), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private void recordCall(Long userId, int promptTokens, int completionTokens,
                            long cost, int status, String errorMsg) {
        try {
            LogAiCall record = new LogAiCall();
            record.setUserId(userId);
            record.setBizType(BIZ_TYPE_RAG);
            record.setModel(props.getModel());
            record.setPromptTokens(promptTokens);
            record.setCompletionTokens(completionTokens);
            record.setLatencyMs((int) Math.min(cost, Integer.MAX_VALUE));
            record.setStatus(status);
            record.setErrorMsg(errorMsg);
            logAiCallMapper.insert(record);
        } catch (Exception e) {
            log.warn("log_ai_call 写入失败（不影响主流程）: {}", e.getMessage());
        }
    }
}