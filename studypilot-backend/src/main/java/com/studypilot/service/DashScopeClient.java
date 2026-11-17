package com.studypilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studypilot.common.ResultCode;
import com.studypilot.config.DashScopeProperties;
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
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 通义千问 DashScope OpenAI 兼容端点封装。
 * Spring AI Alibaba GA 要求 Boot 3.4+，与本项目锁定的 3.3.5 不兼容，
 * 故按规格采用封装后端服务方案，零新增依赖。
 */
@Slf4j
@Service
public class DashScopeClient {

    private static final int BIZ_TYPE_ANALYSIS = 2;

    private final DashScopeProperties props;
    private final LogAiCallMapper logAiCallMapper;
    private final RestClient restClient;
    private final Semaphore semaphore;

    public DashScopeClient(DashScopeProperties props, LogAiCallMapper logAiCallMapper) {
        this.props = props;
        this.logAiCallMapper = logAiCallMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.semaphore = new Semaphore(Math.max(1, props.getMaxConcurrent()));
    }

    public String getModel() {
        return props.getModel();
    }

    /**
     * 发起一次 JSON 结构化对话，返回模型输出的 JSON 字符串。
     * 失败抛出脱敏 BusinessException；每次调用（含失败）记录 log_ai_call，不含密钥。
     */
    public String chatJson(Long userId, String systemPrompt, String userPrompt) {
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
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2);
            JsonNode resp = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            long cost = System.currentTimeMillis() - start;
            JsonNode usage = resp == null ? null : resp.path("usage");
            if (usage != null) {
                promptTokens = usage.path("prompt_tokens").asInt(0);
                completionTokens = usage.path("completion_tokens").asInt(0);
            }
            String content = resp == null ? "" : resp.path("choices").path(0)
                    .path("message").path("content").asText("");
            if (content.isBlank()) {
                recordCall(userId, promptTokens, completionTokens, cost, 1, "模型返回空内容");
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 返回结果为空，请重试");
            }
            recordCall(userId, promptTokens, completionTokens, cost, 0, "");
            log.info("DashScope 调用成功 model={} latencyMs={} promptTokens={} completionTokens={}",
                    props.getModel(), cost, promptTokens, completionTokens);
            return content;
        } catch (RestClientResponseException e) {
            long cost = System.currentTimeMillis() - start;
            int http = e.getStatusCode().value();
            String message;
            int logStatus = 1;
            if (http == 429) {
                message = "AI 服务限流，请稍后重试";
                logStatus = 3;
            } else if (http == 401 || http == 403) {
                message = "AI 服务认证失败，请检查 API Key 配置";
            } else if (http >= 500) {
                message = "AI 服务暂时不可用，请稍后重试";
            } else {
                message = "AI 请求被拒绝（HTTP " + http + "），请稍后重试";
            }
            recordCall(userId, promptTokens, completionTokens, cost, logStatus, message);
            log.warn("DashScope 调用失败 model={} http={} latencyMs={} body={}",
                    props.getModel(), http, cost, truncate(e.getResponseBodyAsString(), 300));
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
        } catch (RestClientException e) {
            long cost = System.currentTimeMillis() - start;
            // 读超时可能包装在 ResourceAccessException 或响应提取异常中，需遍历 cause 链
            boolean timeout = false;
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof SocketTimeoutException) {
                    timeout = true;
                    break;
                }
            }
            String message = timeout ? "AI 服务响应超时，请稍后重试" : "AI 服务网络异常，请稍后重试";
            recordCall(userId, promptTokens, completionTokens, cost, timeout ? 2 : 1, message);
            log.warn("DashScope 网络异常 model={} latencyMs={} timeout={} cause={}",
                    props.getModel(), cost, timeout, e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
        } finally {
            semaphore.release();
        }
    }

    private void recordCall(Long userId, int promptTokens, int completionTokens,
                            long cost, int status, String errorMsg) {
        try {
            LogAiCall record = new LogAiCall();
            record.setUserId(userId);
            record.setBizType(BIZ_TYPE_ANALYSIS);
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

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}