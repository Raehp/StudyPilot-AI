package com.studypilot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文档向量化处理状态 */
@Getter
@AllArgsConstructor
public enum VectorStatus {

    PENDING(0, "PENDING"),
    PROCESSING(1, "PROCESSING"),
    SUCCESS(2, "SUCCESS"),
    FAILED(3, "FAILED");

    private final int code;
    private final String externalName;

    public static VectorStatus fromCode(Integer code) {
        if (code != null) {
            for (VectorStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        return PENDING;
    }
}