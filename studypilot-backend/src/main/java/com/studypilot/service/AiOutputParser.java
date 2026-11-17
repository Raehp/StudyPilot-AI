package com.studypilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.ResultCode;
import com.studypilot.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 输出解析公共工具：去除 Markdown 代码围栏 + 反序列化为结构化 DTO。
 * 解析失败抛出脱敏 BusinessException，绝不把无法解析的输出当作有效结果保存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOutputParser {

    private final ObjectMapper objectMapper;

    public <T> T parse(String json, Class<T> type) {
        try {
            T value = objectMapper.readValue(stripFences(json), type);
            if (value == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 返回结果为空，请重试");
            }
            return value;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 输出 JSON 解析失败 type={} err={}", type.getSimpleName(), e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 返回结果格式异常，请重试");
        }
    }

    public String stripFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}