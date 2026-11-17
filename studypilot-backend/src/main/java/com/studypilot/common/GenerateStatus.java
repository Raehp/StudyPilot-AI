package com.studypilot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 异步生成状态（学习计划 / 智能出题共用）：0-未生成 1-生成中 2-成功 3-失败 */
@Getter
@AllArgsConstructor
public enum GenerateStatus {

    NONE(0, "NONE"),
    GENERATING(1, "GENERATING"),
    SUCCESS(2, "SUCCESS"),
    FAILED(3, "FAILED");

    private final int code;
    private final String externalName;

    public static GenerateStatus fromCode(Integer code) {
        if (code != null) {
            for (GenerateStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        return NONE;
    }
}