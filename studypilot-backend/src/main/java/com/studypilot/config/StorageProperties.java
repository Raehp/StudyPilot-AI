package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "studypilot.storage")
public class StorageProperties {

    /** 文件持久化根目录，可通过 STORAGE_DIR 环境变量覆盖 */
    private String uploadDir = "./data/uploads";

    /** 单文件上传上限（MB），集中配置 */
    private int maxSizeMb = 30;
}