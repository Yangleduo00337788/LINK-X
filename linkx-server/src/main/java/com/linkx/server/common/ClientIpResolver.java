package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

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
 *
 * 最终统一规范为 IPv4 展示形式（如 ::1 → 127.0.0.1，::ffff:x.x.x.x → x.x.x.x）。
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
                            return normalizeToIpv4(ip);
                        }
                    }
                    // XFF 全部都是代理 IP（异常情况），回退到第一个
                    return normalizeToIpv4(parts[0].trim());
                }
                String realIp = request.getHeader("X-Real-IP");
                if (StringUtils.hasText(realIp)) {
                    return normalizeToIpv4(realIp.trim());
                }
            }
        }
        String remote = request.getRemoteAddr();
        return normalizeToIpv4(remote != null ? remote : "unknown");
    }

    /**
     * 将常见 IPv6 形式规范为 IPv4，便于日志与管理端展示。
     * <ul>
     *   <li>{@code ::1} / {@code 0:0:0:0:0:0:0:1} → {@code 127.0.0.1}</li>
     *   <li>{@code ::ffff:192.168.1.1} → {@code 192.168.1.1}</li>
     *   <li>已是 IPv4 则原样返回</li>
     * </ul>
     */
    public static String normalizeToIpv4(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        String v = ip.trim();
        if ("unknown".equalsIgnoreCase(v)) {
            return v;
        }
        // 去掉可能的端口（IPv4:port），IPv6 带端口多为 [addr]:port
        if (v.startsWith("[") && v.contains("]")) {
            int end = v.indexOf(']');
            v = v.substring(1, end);
        } else if (v.chars().filter(ch -> ch == '.').count() == 3 && v.indexOf(':') > 0) {
            v = v.substring(0, v.lastIndexOf(':'));
        }

        String lower = v.toLowerCase(Locale.ROOT);
        if ("::1".equals(lower) || "0:0:0:0:0:0:0:1".equals(lower)) {
            return "127.0.0.1";
        }

        // IPv4-mapped IPv6: ::ffff:192.168.0.1 or ::ffff:c0a8:1
        if (lower.startsWith("::ffff:")) {
            String mapped = v.substring("::ffff:".length());
            if (mapped.indexOf('.') >= 0) {
                return mapped;
            }
            String[] hexs = mapped.split(":");
            if (hexs.length == 2) {
                try {
                    int hi = Integer.parseInt(hexs[0], 16);
                    int lo = Integer.parseInt(hexs[1], 16);
                    return ((hi >> 8) & 0xff) + "." + (hi & 0xff) + "."
                            + ((lo >> 8) & 0xff) + "." + (lo & 0xff);
                } catch (NumberFormatException ignored) {
                    return v;
                }
            }
        }

        return v;
    }
}
