package com.linkx.server.service.impl;

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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "linkx:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    // Lua 脚本：原子性地获取并删除验证码，防止竞态条件
    private static final String VALIDATE_CAPTCHA_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local code = ARGV[1] " +
            "local expected = redis.call('get', key) " +
            "if not expected then return -1 end " +  // -1: 验证码不存在或已过期
            "if string.lower(expected) ~= string.lower(code) then " +
            "    redis.call('del', key) " +  // 验证失败也删除，防止暴力破解
            "    return 0 " +  // 0: 验证码错误
            "end " +
            "redis.call('del', key) " +
            "return 1";  // 1: 验证成功

    private final StringRedisTemplate redisTemplate;

    @Override
    public CaptchaVO generate() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, code, CAPTCHA_TTL);

        return CaptchaVO.builder()
                .captchaId(captchaId)
                .imageBase64(renderImageBase64(code))
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

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(VALIDATE_CAPTCHA_LUA_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(key), trimmedCode);

        if (result == null || result == -1) {
            throw new CustomException(400, "验证码已过期，请重新获取");
        }
        if (result == 0) {
            throw new CustomException(400, "验证码错误");
        }
        // result == 1: 验证成功，验证码已被原子删除
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String renderImageBase64(String code) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, width, height);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180)));
                g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(20 + random.nextInt(110), 20 + random.nextInt(110), 20 + random.nextInt(110)));
                g.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 28 + random.nextInt(6));
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
