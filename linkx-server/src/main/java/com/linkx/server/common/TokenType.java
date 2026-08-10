package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TokenType fromClaim(String claim) {
        if (claim == null) {
            // fail-safe：缺失类型声明视为无效，避免被错误识别为 ACCESS 绕过类型校验
            throw new IllegalArgumentException("token 类型声明缺失");
        }
        for (TokenType type : values()) {
            if (type.value.equalsIgnoreCase(claim)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的令牌类型");
    }
}
