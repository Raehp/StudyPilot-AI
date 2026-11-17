package com.studypilot.security;

public final class AuthConstants {

    public static final String TOKEN_KEY_PREFIX = "studypilot:auth:token:";
    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private AuthConstants() {
    }

    public static String tokenKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}