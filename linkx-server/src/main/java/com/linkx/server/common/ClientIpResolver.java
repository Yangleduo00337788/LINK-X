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
 * 开启 trustProxy 后：仅当请求直连来源（remoteAddr）属于 trustedIps 配置的
 * 反向代理 IP 时，才信任 X-Forwarded-For / X-Real-IP；否则一律回退到
 * remoteAddr。trustedIps 为空时拒绝任何 XFF，强制要求显式配置反代 IP，
 * 防止任意来源伪造 IP 绕过限流。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, LinkxProperties props) {
        if (props != null && props.getProxy().isTrustProxy()) {
            List<String> trusted = props.getProxy().getTrustedIps();
            // trustedIps 必须显式配置且包含直连来源，才信任 XFF/X-Real-IP
            if (trusted != null && !trusted.isEmpty()
                    && trusted.contains(request.getRemoteAddr())) {
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
