package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.CaptchaScope;
import com.linkx.server.common.CaptchaType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.CaptchaVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "linkx:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    /** 排除易混淆字符 I/O/0/1，仅大写 + 数字，便于人工辨认 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String VALIDATE_CAPTCHA_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local code = ARGV[1] " +
            "local expected = redis.call('get', key) " +
            "if not expected then return -1 end " +
            "if expected ~= code then " +
            "    redis.call('del', key) " +
            "    return 0 " +
            "end " +
            "redis.call('del', key) " +
            "return 1";

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;

    @Override
    public CaptchaType resolveType(CaptchaScope scope) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        if (scope == CaptchaScope.ADMIN) {
            return CaptchaType.fromWire(auth.getAdminCaptchaType());
        }
        return CaptchaType.fromWire(auth.getClientCaptchaType());
    }

    @Override
    public CaptchaVO generate(CaptchaScope scope) {
        CaptchaType type = resolveType(scope);
        if (type == CaptchaType.SLIDER) {
            return generateSlider();
        }
        return generateImage();
    }

    @Override
    public CaptchaVO generateForOwner(String ownerId) {
        CaptchaType type = resolveType(CaptchaScope.CLIENT);
        if (type == CaptchaType.SLIDER) {
            return generateSliderForOwner(ownerId);
        }
        return generateImageForOwner(ownerId);
    }

    private CaptchaVO generateImage() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, code, CAPTCHA_TTL);
        return CaptchaVO.builder()
                .type(CaptchaType.IMAGE.toWire())
                .captchaId(captchaId)
                .imageBase64(renderImageBase64(code))
                .expireSeconds(CAPTCHA_TTL.toSeconds())
                .build();
    }

    private CaptchaVO generateSlider() {
        SliderCaptchaRenderer.SliderAssets assets = SliderCaptchaRenderer.generate(SECURE_RANDOM);
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, assets.getStoredValue(), CAPTCHA_TTL);
        return CaptchaVO.builder()
                .type(CaptchaType.SLIDER.toWire())
                .captchaId(captchaId)
                .imageBase64(assets.getBackgroundBase64())
                .puzzleImageBase64(assets.getPuzzleBase64())
                .puzzleY(assets.getPuzzleY())
                .expireSeconds(CAPTCHA_TTL.toSeconds())
                .build();
    }

    private CaptchaVO generateImageForOwner(String ownerId) {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        String boundValue = ownerId + "|" + code;
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + "owner:" + captchaId, boundValue, CAPTCHA_TTL);
        return CaptchaVO.builder()
                .type(CaptchaType.IMAGE.toWire())
                .captchaId(captchaId)
                .imageBase64(renderImageBase64(code))
                .expireSeconds(CAPTCHA_TTL.toSeconds())
                .build();
    }

    private CaptchaVO generateSliderForOwner(String ownerId) {
        SliderCaptchaRenderer.SliderAssets assets = SliderCaptchaRenderer.generate(SECURE_RANDOM);
        String captchaId = UUID.randomUUID().toString();
        String boundValue = ownerId + "|" + assets.getStoredValue();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + "owner:" + captchaId, boundValue, CAPTCHA_TTL);
        return CaptchaVO.builder()
                .type(CaptchaType.SLIDER.toWire())
                .captchaId(captchaId)
                .imageBase64(assets.getBackgroundBase64())
                .puzzleImageBase64(assets.getPuzzleBase64())
                .puzzleY(assets.getPuzzleY())
                .expireSeconds(CAPTCHA_TTL.toSeconds())
                .build();
    }

    @Override
    public void validate(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new CustomException(400, "请填写验证码");
        }

        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String trimmedCode = captchaCode.trim();
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new CustomException(400, "验证码已过期，请重新获取");
        }
        if (SliderCaptchaRenderer.isSliderValue(stored)) {
            validateSliderAndDelete(key, stored, trimmedCode);
            return;
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(VALIDATE_CAPTCHA_LUA_SCRIPT);
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), trimmedCode);
        handleValidateResult(result);
    }

    @Override
    public void validateForOwner(String ownerId, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new CustomException(400, "请填写验证码");
        }

        String key = CAPTCHA_KEY_PREFIX + "owner:" + captchaId;
        String trimmedCode = captchaCode.trim();
        String bound = redisTemplate.opsForValue().get(key);
        if (bound == null) {
            throw new CustomException(400, "验证码已过期，请重新获取");
        }

        int delim = bound.indexOf('|');
        if (delim <= 0) {
            redisTemplate.delete(key);
            throw new CustomException(400, "验证码已过期，请重新获取");
        }
        String storedOwner = bound.substring(0, delim);
        String storedCode = bound.substring(delim + 1);
        if (!ownerId.equals(storedOwner)) {
            throw new CustomException(400, "验证码与账号不匹配，请重新获取");
        }

        if (SliderCaptchaRenderer.isSliderValue(storedCode)) {
            if (!SliderCaptchaRenderer.matchesSlider(storedCode, trimmedCode)) {
                redisTemplate.delete(key);
                throw new CustomException(400, "验证码错误");
            }
            redisTemplate.delete(key);
            return;
        }

        if (!constantTimeEquals(storedCode, trimmedCode)) {
            redisTemplate.delete(key);
            throw new CustomException(400, "验证码错误");
        }
        redisTemplate.delete(key);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    private void validateSliderAndDelete(String key, String stored, String submitted) {
        if (!SliderCaptchaRenderer.matchesSlider(stored, submitted)) {
            redisTemplate.delete(key);
            throw new CustomException(400, "验证码错误");
        }
        redisTemplate.delete(key);
    }

    private void handleValidateResult(Long result) {
        if (result == null || result == -1) {
            throw new CustomException(400, "验证码已过期，请重新获取");
        }
        if (result == 0) {
            throw new CustomException(400, "验证码错误");
        }
    }

    @Override
    public boolean isEnabled() {
        return linkxProperties.getAuth().isCaptchaEnabled();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(SECURE_RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String renderImageBase64(String code) {
        int width = 140;
        int height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(245, 247, 250), width, height, new Color(232, 238, 245));
            g.setPaint(gradient);
            g.fillRect(0, 0, width, height);

            for (int i = 0; i < 40; i++) {
                g.setColor(new Color(180 + SECURE_RANDOM.nextInt(50), 185 + SECURE_RANDOM.nextInt(40), 195 + SECURE_RANDOM.nextInt(40), 80));
                g.fillOval(SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height), 2, 2);
            }
            for (int i = 0; i < 4; i++) {
                g.setColor(new Color(160 + SECURE_RANDOM.nextInt(60), 165 + SECURE_RANDOM.nextInt(50), 175 + SECURE_RANDOM.nextInt(50), 90));
                g.setStroke(new BasicStroke(1.0f));
                g.drawLine(SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height), SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height));
            }

            g.setFont(new Font("Arial", Font.BOLD, 26));
            int slot = width / Math.max(code.length(), 1);
            for (int i = 0; i < code.length(); i++) {
                String charStr = String.valueOf(code.charAt(i));
                g.setColor(new Color(20 + SECURE_RANDOM.nextInt(70), 25 + SECURE_RANDOM.nextInt(70), 40 + SECURE_RANDOM.nextInt(80)));

                AffineTransform old = g.getTransform();
                double cx = slot * i + slot / 2.0;
                double cy = height / 2.0;
                double angle = (SECURE_RANDOM.nextDouble() - 0.5) * Math.PI / 9;
                g.rotate(angle, cx, cy);
                FontMetrics fm = g.getFontMetrics();
                int x = (int) (cx - fm.stringWidth(charStr) / 2.0);
                int y = (int) (cy + fm.getAscent() / 2.0 - 2);
                g.drawString(charStr, x, y);
                g.setTransform(old);
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new CustomException(500, "验证码生成失败");
        }
    }
}
