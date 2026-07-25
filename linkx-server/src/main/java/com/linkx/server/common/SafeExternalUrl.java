package com.linkx.server.common;

import com.linkx.server.exception.CustomException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 外链拉取 SSRF 防护：仅 http(s)、拒绝内网/本机/链路本地与带 userinfo 的 URL。
 */
public final class SafeExternalUrl {

    private SafeExternalUrl() {
    }

    public static URI parseAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(400, "外链地址不能为空");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new CustomException(400, "图片地址过长");
        }
        final URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, "无效的外链地址");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new CustomException(400, "无效的外链地址");
        }
        String schemeLower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(schemeLower) && !"https".equals(schemeLower)) {
            throw new CustomException(400, "仅支持 http/https 外链");
        }
        if (uri.getUserInfo() != null) {
            throw new CustomException(400, "外链不可包含认证信息");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new CustomException(400, "无效的外链主机");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(hostLower)
                || hostLower.endsWith(".localhost")
                || "metadata.google.internal".equals(hostLower)) {
            throw new CustomException(400, "不允许访问内网地址");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new CustomException(400, "不允许访问内网地址");
                }
                byte[] bytes = addr.getAddress();
                // 额外拦截 CGNAT / 链路本地等常见云元数据段
                if (bytes.length == 4) {
                    int b0 = bytes[0] & 0xff;
                    int b1 = bytes[1] & 0xff;
                    if (b0 == 169 && b1 == 254) {
                        throw new CustomException(400, "不允许访问内网地址");
                    }
                    if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                        throw new CustomException(400, "不允许访问内网地址");
                    }
                }
            }
        } catch (UnknownHostException e) {
            throw new CustomException(400, "无法解析外链主机");
        }
        return uri;
    }
}
