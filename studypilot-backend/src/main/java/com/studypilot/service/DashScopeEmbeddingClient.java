package com.studypilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studypilot.common.ResultCode;
import com.studypilot.config.EmbeddingProperties;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * DashScope Embedding 封装（OpenAI 兼容 /embeddings 端点）。
 * 批量处理、超时/限流/失败脱敏映射、log_ai_call 落库（biz_type=3 RAG，不含密钥）。
 * 仅后端调用，前端不允许直连。
 */
@Slf4j
@Service
public class DashScopeEmbeddingClient {

    private static final int BIZ_TYPE_RAG = 3;

    private final EmbeddingProperties props;
    private final LogAiCallMapper logAiCallMapper;
    private final RestClient restClient;
    private final Semaphore semaphore;

    public DashScopeEmbeddingClient(EmbeddingProperties props, LogAiCallMapper logAiCallMapper) {
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

    public int getDimension() {
        return props.getDimension();
    }

    public String getVersion() {
        return props.getVersion();
    }

    public int getBatchSize() {
        return Math.max(1, Math.min(props.getBatchSize(), 10));
    }

    /** 批量嵌入，返回与输入顺序一致的向量列表；失败抛脱敏 BusinessException */
    public List<float[]> embedBatch(Long userId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.size() > getBatchSize()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单批 Embedding 数量超出上限 " + getBatchSize());
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
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Embedding 调用被中断，请重试");
        }
        if (!acquired) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Embedding 服务繁忙，请稍后重试");
        }
        long start = System.currentTimeMillis();
        int promptTokens = 0;
        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "input", texts,
                    "dimensions", props.getDimension(),
                    "encoding_format", "float");
            JsonNode resp = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            long cost = System.currentTimeMillis() - start;
            JsonNode usage = resp == null ? null : resp.path("usage");
            if (usage != null) {
                promptTokens = usage.path("prompt_tokens").asInt(0);
            }
            JsonNode data = resp == null ? null : resp.path("data");
            if (data == null || !data.isArray() || data.size() != texts.size()) {
                recordCall(userId, promptTokens, cost, 1, "Embedding 返回结果数量不一致");
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Embedding 返回结果异常，请重试");
            }
            List<JsonNode> ordered = new ArrayList<>();
            data.forEach(ordered::add);
            ordered.sort(Comparator.comparingInt(n -> n.path("index").asInt(0)));
            List<float[]> vectors = new ArrayList<>(ordered.size());
            for (JsonNode item : ordered) {
                JsonNode arr = item.path("embedding");
                if (!arr.isArray() || arr.size() != props.getDimension()) {
                    recordCall(userId, promptTokens, cost, 1, "Embedding 返回维度不一致");
                    throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                            "Embedding 返回维度与配置不一致（期望 " + props.getDimension() + "）");
                }
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    double v = arr.get(i).asDouble(Double.NaN);
                    if (!Double.isFinite(v)) {
                        recordCall(userId, promptTokens, cost, 1, "Embedding 返回非法数值");
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Embedding 返回非法数值，请重试");
                    }
                    vec[i] = (float) v;
                }
                vectors.add(vec);
            }
            recordCall(userId, promptTokens, cost, 0, "");
            log.info("Embedding 调用成功 model={} batch={} dim={} latencyMs={} promptTokens={}",
                    props.getModel(), texts.size(), props.getDimension(), cost, promptTokens);
            return vectors;
        } catch (RestClientResponseException e) {
            long cost = System.currentTimeMillis() - start;
            int http = e.getStatusCode().value();
            String message;
            int logStatus = 1;
            if (http == 429) {
                message = "Embedding 服务限流，请稍后重试";
                logStatus = 3;
            } else if (http == 401 || http == 403) {
                message = "Embedding 服务认证失败，请检查 API Key 配置";
            } else if (http >= 500) {
                message = "Embedding 服务暂时不可用，请稍后重试";
            } else {
                message = "Embedding 请求被拒绝（HTTP " + http + "），请稍后重试";
            }
            recordCall(userId, promptTokens, cost, logStatus, message);
            log.warn("Embedding 调用失败 model={} http={} latencyMs={} body={}",
                    props.getModel(), http, cost, truncate(e.getResponseBodyAsString(), 300));
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
        } catch (RestClientException e) {
            long cost = System.currentTimeMillis() - start;
            boolean timeout = false;
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof SocketTimeoutException) {
                    timeout = true;
                    break;
                }
            }
            String message = timeout ? "Embedding 服务响应超时，请稍后重试" : "Embedding 服务网络异常，请稍后重试";
            recordCall(userId, promptTokens, cost, timeout ? 2 : 1, message);
            log.warn("Embedding 网络异常 model={} latencyMs={} timeout={} cause={}",
                    props.getModel(), cost, timeout, e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, message);
        } finally {
            semaphore.release();
        }
    }

    private void recordCall(Long userId, int promptTokens, long cost, int status, String errorMsg) {
        try {
            LogAiCall record = new LogAiCall();
            record.setUserId(userId);
            record.setBizType(BIZ_TYPE_RAG);
            record.setModel(props.getModel());
            record.setPromptTokens(promptTokens);
            record.setCompletionTokens(0);
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