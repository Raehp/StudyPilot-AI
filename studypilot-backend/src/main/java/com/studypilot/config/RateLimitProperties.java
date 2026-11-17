package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "studypilot.rate-limit")
public class RateLimitProperties {

    /** 是否启用接口频率限制 */
    private boolean enabled = true;

    /** 固定窗口长度（秒） */
    private int windowSeconds = 60;

    /** 认证桶（登录/注册）每窗口每 IP 上限 */
    private int authLimit = 10;

    /** AI 生成桶（分析/向量化/计划生成/出题/RAG）每窗口每 IP 上限 */
    private int aiLimit = 30;

    /** 上传桶每窗口每 IP 上限 */
    private int uploadLimit = 30;
}