package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** DashScope Embedding 接入配置（studypilot.embedding.*），Key 仅从环境变量读取 */
@Data
@Component
@ConfigurationProperties(prefix = "studypilot.embedding")
public class EmbeddingProperties {

    private String apiKey = "";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String model = "text-embedding-v3";

    private int dimension = 1024;

    /** 版本标识：与模型/维度共同构成向量可比性三元组 */
    private String version = "v3";

    /** 批量大小（DashScope text-embedding-v3 单请求上限 10 条） */
    private int batchSize = 10;

    private int connectTimeoutMs = 10000;

    private int readTimeoutMs = 60000;

    private int maxConcurrent = 2;
}