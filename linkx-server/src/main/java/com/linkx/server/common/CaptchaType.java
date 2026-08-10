package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
/**
 * 人机验证码形态：图形字符码 / 滑块拼图。
 */
public enum CaptchaType {
    IMAGE,
    SLIDER;

    public static CaptchaType fromWire(String value) {
        if (value == null || value.isBlank()) {
            return IMAGE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IMAGE;
        }
    }

    public String toWire() {
        return name().toLowerCase();
    }
}
