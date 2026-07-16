package com.linkx.server.common;

import com.linkx.server.config.LinkxProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户端 IP 解析工具。
 *
 * 默认行为（未开启 trustProxy）：仅使用 socket.getRemoteAddr()，
 * 避免攻击者伪造 X-Forwarded-For 绕过限流。
 *
 * 开启 trustProxy 后：优先使用 X-Forwarded-For 的第一个 IP；
 * 若配置 trustedIps 则只信任来自该列表的代理传来的头，
 * 防止任意来源伪造 IP。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, LinkxProperties props) {
        if (props != null && props.getProxy().isTrustProxy()) {
            List<String> trusted = props.getProxy().getTrustedIps();
            if (trusted == null || trusted.isEmpty() || trusted.contains(request.getRemoteAddr())) {
                String xff = request.getHeader("X-Forwarded-For");
                if (StringUtils.hasText(xff)) {
                    return xff.split(",")[0].trim();
                }
                String realIp = request.getHeader("X-Real-IP");
                if (StringUtils.hasText(realIp)) {
                    return realIp.trim();
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
