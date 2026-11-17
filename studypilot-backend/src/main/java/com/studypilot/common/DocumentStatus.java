package com.studypilot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档流水线状态。M2 仅使用 UPLOADED/PARSING/READY/FAILED，
 * CHUNKING/EMBEDDING 为 M4 预留；对外暴露 externalName。
 */
@Getter
@AllArgsConstructor
public enum DocumentStatus {

    UPLOADED(0, "PENDING"),
    PARSING(1, "PROCESSING"),
    CHUNKING(2, "CHUNKING"),
    EMBEDDING(3, "EMBEDDING"),
    READY(4, "SUCCESS"),
    FAILED(5, "FAILED");

    private final int code;
    private final String externalName;

    public static DocumentStatus fromCode(Integer code) {
        if (code != null) {
            for (DocumentStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        return UPLOADED;
    }
}