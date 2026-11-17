package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "studypilot.ai")
public class DashScopeProperties {

    /** 仅从环境变量 DASHSCOPE_API_KEY 注入，缺省为空（调用时给出明确业务错误） */
    private String apiKey = "";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String model = "qwen-plus";

    private int connectTimeoutMs = 10000;

    private int readTimeoutMs = 120000;

    /** 全局同时进行的模型调用上限 */
    private int maxConcurrent = 2;

    /** 分段分析单段字符数 */
    private int segmentChars = 6000;

    /** 超长论文最多抽取段数（均匀采样，含首尾段） */
    private int maxSegments = 12;
}