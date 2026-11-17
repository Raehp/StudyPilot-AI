package com.studypilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 文本切片参数集中配置（studypilot.chunk.*） */
@Data
@Component
@ConfigurationProperties(prefix = "studypilot.chunk")
public class ChunkProperties {

    /** 目标切片长度（字符） */
    private int targetChars = 500;

    /** 相邻切片重叠长度（字符，按行边界取整） */
    private int overlapChars = 80;

    /** 最小切片长度，低于此值并入前一切片 */
    private int minChunkChars = 50;

    /** 单文档切片数量上限（超出则任务失败，防止异常文档耗尽资源） */
    private int maxChunksPerDoc = 2000;
}