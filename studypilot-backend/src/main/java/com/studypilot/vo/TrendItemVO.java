package com.studypilot.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用趋势点：日期(MM-dd) + 当日计数。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendItemVO {
    private String date;
    private long count;
}