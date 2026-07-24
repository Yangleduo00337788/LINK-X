package com.linkx.server.common;

import org.springframework.util.StringUtils;

/**
 * 敏感信息脱敏工具：邮箱、手机、IP、验证码等。
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 1) {
            return "*@" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }

    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() >= 13) {
            digits = digits.substring(2);
        }
        if (digits.length() < 7) {
            return phone.length() <= 2 ? "**" : phone.charAt(0) + "****";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /** IPv4/IPv6 粗粒度脱敏，日志与审计展示用。 */
    public static String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        String v = ip.trim();
        if (v.contains(".")) {
            String[] parts = v.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
        }
        if (v.contains(":")) {
            int first = v.indexOf(':');
            int second = v.indexOf(':', first + 1);
            if (second > 0) {
                return v.substring(0, second) + ":*:*";
            }
            return v.charAt(0) + "****";
        }
        return v.length() <= 4 ? "****" : v.substring(0, 2) + "****";
    }

    /** 验证码：仅保留末 1～2 位，避免日志明文。 */
    public static String maskCode(String code) {
        if (!StringUtils.hasText(code)) {
            return code;
        }
        if (code.length() <= 2) {
            return "**";
        }
        return "****" + code.substring(code.length() - 2);
    }
}
