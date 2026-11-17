package com.studypilot.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用统计项：标签 + 数值（用于分布类图表）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatItemVO {
    private String label;
    private long value;
}