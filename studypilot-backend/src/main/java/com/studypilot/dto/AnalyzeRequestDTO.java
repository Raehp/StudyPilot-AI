package com.studypilot.dto;

import lombok.Data;

@Data
public class AnalyzeRequestDTO {

    /** true = 确认重新分析并覆盖当前展示结果（历史任务保留） */
    private Boolean reanalyze;
}