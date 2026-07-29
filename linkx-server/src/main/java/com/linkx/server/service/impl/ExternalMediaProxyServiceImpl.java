package com.linkx.server.service.impl;

import com.linkx.server.common.SafeExternalUrl;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.ExternalMediaProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalMediaProxyServiceImpl implements ExternalMediaProxyService {

    /** 与朋友圈长会话对齐；前端仍将代理 URL 视为临时地址不落盘 */
    private static final long PROXY_TTL_SECONDS = 6 * 3600L;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 15 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final LinkxProperties linkxProperties;

    @Override
    public String wrapExternalUrl(String absoluteHttpUrl) {
        if (!StringUtils.hasText(absoluteHttpUrl)) {
            return absoluteHttpUrl;
        }
        URI uri = SafeExternalUrl.parseAndValidate(absoluteHttpUrl);
        String canonical = uri.toString();
        long exp = Instant.now().getEpochSecond() + PROXY_TTL_SECONDS;
        String sig = sign(canonical, exp);
        return "/media/external?u=" + URLEncoder.encode(canonical, StandardCharsets.UTF_8)
                + "&e=" + exp
                + "&s=" + sig;
    }

    @Override
    public ProxiedImage fetch(String url, long expiresEpochSec, String signature) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(signature)) {
            throw new CustomException(400, "无效的代理参数");
        }
        long now = Instant.now().getEpochSecond();
        if (expiresEpochSec < now) {
            throw new CustomException(403, "代理链接已过期");
        }
        if (expiresEpochSec > now + PROXY_TTL_SECONDS + 60) {
            throw new CustomException(403, "代理签名无效");
        }
        // 先按调用方 URL 验签（与签发时 canonical 一致），再允许安全跟随跳转
        SafeExternalUrl.Validated validated = SafeExternalUrl.parseAndValidatePinned(url);
        URI uri = validated.uri();
        String canonical = uri.toString();
        String expected = sign(canonical, expiresEpochSec);
        if (!constantTimeEquals(expected, signature.trim().toLowerCase(Locale.ROOT))) {
            throw new CustomException(403, "代理签名无效");
        }

        HttpURLConnection conn = null;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (conn != null) {
                    conn.disconnect();
                }
                conn = SafeExternalUrl.openPinnedConnection(validated);
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "LinkX-MediaProxy/1.0");
                conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
                // 不转发 Referer，降低防盗链与追踪耦合
                int code = conn.getResponseCode();
                if (isRedirect(code)) {
                    if (hop >= MAX_REDIRECTS) {
                        throw new CustomException(502, "外链图片不可用");
                    }
                    String location = conn.getHeaderField("Location");
                    if (!StringUtils.hasText(location)) {
                        throw new CustomException(502, "外链图片不可用");
                    }
                    URI next = validated.uri().resolve(location.trim());
                    validated = SafeExternalUrl.parseAndValidatePinned(next.toString());
                    continue;
                }
                if (code != 200) {
                    throw new CustomException(502, "外链图片不可用");
                }
                String contentType = conn.getContentType();
                if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    throw new CustomException(400, "外链不是图片资源");
                }
                long contentLength = conn.getContentLengthLong();
                if (contentLength > MAX_BYTES) {
                    throw new CustomException(400, "外链图片过大");
                }
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    int total = 0;
                    while ((n = in.read(buf)) >= 0) {
                        total += n;
                        if (total > MAX_BYTES) {
                            throw new CustomException(400, "外链图片过大");
                        }
                        out.write(buf, 0, n);
                    }
                    String ct = contentType.split(";", 2)[0].trim();
                    return new ProxiedImage(out.toByteArray(), ct);
                }
            }
            throw new CustomException(502, "外链图片不可用");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("外链代拉失败: host={}, err={}", uri.getHost(), e.getMessage());
            throw new CustomException(502, "外链图片拉取失败");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static final String PROXY_KEY_PURPOSE = "linkx-media-proxy-v1";

    private String sign(String url, long exp) {
        String jwtSecret = linkxProperties.getJwt().getSecret();
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT secret 未配置");
        }
        try {
            // 使用 HKDF-like 方式从 JWT secret 派生独立的代理签名密钥
            Mac prkMac = Mac.getInstance("HmacSHA256");
            prkMac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] prk = prkMac.doFinal(PROXY_KEY_PURPOSE.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            // 使用完整 HMAC 输出（64 hex chars = 256 bits），不做截断
            return HexFormat.of().formatHex(mac.doFinal((url + "|" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
