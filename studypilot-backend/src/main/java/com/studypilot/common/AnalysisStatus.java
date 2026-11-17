package com.studypilot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AnalysisStatus {

    PENDING(0, "PENDING"),
    PROCESSING(1, "PROCESSING"),
    SUCCESS(2, "SUCCESS"),
    FAILED(3, "FAILED");

    private final int code;
    private final String externalName;

    public static AnalysisStatus fromCode(Integer code) {
        if (code != null) {
            for (AnalysisStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        return PENDING;
    }
}