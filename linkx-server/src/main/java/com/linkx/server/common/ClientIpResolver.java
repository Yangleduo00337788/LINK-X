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
                    // 反向遍历 XFF 链：跳过所有在 trustedIps 中的代理 IP，
                    // 返回第一个"非代理"的 IP（即真实客户端）。
                    // 防攻击者伪造最左 IP（最左 IP 总是可被任意客户端控制）。
                    String[] parts = xff.split(",");
                    for (int i = parts.length - 1; i >= 0; i--) {
                        String ip = parts[i].trim();
                        if (StringUtils.hasText(ip) && !trusted.contains(ip)) {
                            return ip;
                        }
                    }
                    // XFF 全部都是代理 IP（异常情况），回退到第一个
                    return parts[0].trim();
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
