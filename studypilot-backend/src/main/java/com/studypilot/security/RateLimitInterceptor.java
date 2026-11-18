package com.studypilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.Result;
import com.studypilot.common.ResultCode;
import com.studypilot.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 轻量接口频率限制：基于 Redis INCR+EXPIRE 固定窗口，按客户端 IP + 路由桶计数。
 * 仅对 POST 写操作（认证/AI 生成/上传）限流，只读接口不限；不解析 X-Forwarded-For 防伪造绕过。
 * 追加于 JwtInterceptor 之前，覆盖 login/register；不改动任何已验收认证代码。
 * Redis 不可用时 fail-open（放行并告警），限流为尽力防护而非安全闸门，避免成为额外单点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "studypilot:ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !properties.isEnabled()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String bucket;
        int limit;
        if ("/api/auth/login".equals(path) || "/api/auth/register".equals(path)
                || "/api/users/me/password".equals(path)) {
            bucket = "auth";
            limit = properties.getAuthLimit();
        } else if ("/api/documents/upload".equals(path)) {
            bucket = "upload";
            limit = properties.getUploadLimit();
        } else if (isAiEndpoint(path)) {
            bucket = "ai";
            limit = properties.getAiLimit();
        } else {
            return true;
        }
        // IP 取 remoteAddr，不信任 X-Forwarded-For（避免伪造头绕过限流）
        String key = KEY_PREFIX + bucket + ":" + request.getRemoteAddr();
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
            }
            if (count != null && count > limit) {
                return reject(response);
            }
        } catch (Exception e) {
            // Redis 不可用：限流尽力防护失效，放行请求（认证仍由 JwtInterceptor 把关），避免额外单点
            log.warn("限流计数不可用，放行请求 bucket={} ip={} : {}", bucket, request.getRemoteAddr(), e.getMessage());
        }
        return true;
    }

    private boolean isAiEndpoint(String path) {
        return path.matches("/api/papers/\\d+/analyze")
                || path.matches("/api/documents/\\d+/vectorize")
                || path.matches("/api/study-plans/\\d+/generate")
                || "/api/quizzes/generate".equals(path)
                || path.matches("/api/chat/sessions/\\d+/messages");
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ResultCode.TOO_MANY_REQUESTS)));
        return false;
    }
}