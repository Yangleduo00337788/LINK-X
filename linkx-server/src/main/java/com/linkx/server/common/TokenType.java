package com.linkx.server.common;

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
            return ACCESS;
        }
        for (TokenType type : values()) {
            if (type.value.equalsIgnoreCase(claim)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的令牌类型");
    }
}
