package com.studypilot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageStatus {

    GENERATING(0),
    SUCCESS(1),
    FAILED(2),
    STOPPED(3);

    private final int code;

    public static MessageStatus fromCode(Integer code) {
        if (code != null) {
            for (MessageStatus s : values()) {
                if (s.code == code) {
                    return s;
                }
            }
        }
        return SUCCESS;
    }
}