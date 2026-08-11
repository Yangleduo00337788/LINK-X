package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.StoredMediaProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StoredMediaProxyServiceImpl implements StoredMediaProxyService {

    private static final String PROXY_KEY_PURPOSE = "linkx-stored-media-v1";

    private final LinkxProperties linkxProperties;

    @Override
    public String wrapObjectKey(String objectKey, int expirySeconds) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        String key = objectKey.trim();
        if (key.contains("..") || key.contains("\\") || key.contains("://")) {
            throw new CustomException(400, "非法对象 key");
        }
        int seconds = expirySeconds > 0 ? expirySeconds : 3600;
        long exp = Instant.now().getEpochSecond() + seconds;
        String sig = sign(key, exp);
        return "/media/stored?k=" + urlEncode(key) + "&e=" + exp + "&s=" + sig;
    }

    @Override
    public String verifyAndExtractKey(String objectKey, long expiresEpochSec, String signature) {
        if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(signature)) {
            throw new CustomException(400, "无效的代理参数");
        }
        String key = objectKey.trim();
        if (key.contains("..") || key.contains("\\") || key.contains("://") || key.startsWith("/")) {
            throw new CustomException(400, "非法对象 key");
        }
        long now = Instant.now().getEpochSecond();
        if (expiresEpochSec < now) {
            throw new CustomException(403, "代理链接已过期");
        }
        String expected = sign(key, expiresEpochSec);
        if (!constantTimeEquals(expected, signature.trim().toLowerCase(Locale.ROOT))) {
            throw new CustomException(403, "代理签名无效");
        }
        return key;
    }

    private String sign(String key, long exp) {
        String jwtSecret = linkxProperties.getJwt().getSecret();
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT secret 未配置");
        }
        try {
            Mac prkMac = Mac.getInstance("HmacSHA256");
            prkMac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] prk = prkMac.doFinal(PROXY_KEY_PURPOSE.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((key + "|" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
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
