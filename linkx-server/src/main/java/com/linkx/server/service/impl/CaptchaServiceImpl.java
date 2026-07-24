package com.linkx.server.service.impl;

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
    /** 排除易混淆字符 I/O/0/1，仅大写 + 数字，便于人工辨认 */
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

    // Lua 脚本：验证账号绑定的验证码（密码重置专用）
    // 原子性：检查 ownerId 绑定 + 验证验证码，全部在一个原子操作中完成
    private static final String VALIDATE_CAPTCHA_FOR_OWNER_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local ownerId = ARGV[1] " +
            "local code = ARGV[2] " +
            "local bound = redis.call('get', key) " +
            "if not bound then return -1 end " +  // -1: 验证码不存在或已过期
            "local delim = string.find(bound, ':') " +
            "if not delim then redis.call('del', key); return -1 end " +  // 数据格式错误
            "local storedOwner = string.sub(bound, 1, delim - 1) " +
            "local storedCode = string.sub(bound, delim + 1) " +
            "if storedOwner ~= ownerId then return -2 end " +  // -2: ownerId 不匹配（不删除 key，防枚举）
            "if string.lower(storedCode) ~= string.lower(code) then " +
            "    redis.call('del', key) " +  // 验证失败删除，防止暴力破解
            "    return 0 " +  // 0: 验证码错误
            "end " +
            "redis.call('del', key) " +
            "return 1";  // 1: 验证成功

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;

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
    public CaptchaVO generateForOwner(String ownerId) {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        // 将验证码与 ownerId 绑定存储：key = "linkx:captcha:owner:{captchaId}", value = "{ownerId}:{code}"
        String boundValue = ownerId + ":" + code;
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + "owner:" + captchaId, boundValue, CAPTCHA_TTL);

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

    @Override
    public void validateForOwner(String ownerId, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new CustomException(400, "请填写验证码");
        }

        String key = CAPTCHA_KEY_PREFIX + "owner:" + captchaId;
        String trimmedCode = captchaCode.trim();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(VALIDATE_CAPTCHA_FOR_OWNER_LUA_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(key), ownerId, trimmedCode);

        if (result == null || result == -1) {
            throw new CustomException(400, "验证码已过期，请重新获取");
        }
        if (result == -2) {
            throw new CustomException(400, "验证码与账号不匹配，请重新获取");
        }
        if (result == 0) {
            throw new CustomException(400, "验证码错误");
        }
        // result == 1: 验证成功，验证码已被原子删除，ownerId 绑定已校验
    }

    @Override
    public boolean isEnabled() {
        return linkxProperties.getAuth().isCaptchaEnabled();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 渲染 4 位验证码：留足边距，轻微旋转，避免被前端缩略裁切后看不全。
     */
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

            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 40; i++) {
                g.setColor(new Color(180 + random.nextInt(50), 185 + random.nextInt(40), 195 + random.nextInt(40), 80));
                g.fillOval(random.nextInt(width), random.nextInt(height), 2, 2);
            }
            for (int i = 0; i < 4; i++) {
                g.setColor(new Color(160 + random.nextInt(60), 165 + random.nextInt(50), 175 + random.nextInt(50), 90));
                g.setStroke(new BasicStroke(1.0f));
                g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }

            g.setFont(new Font("Arial", Font.BOLD, 26));
            int slot = width / Math.max(code.length(), 1);
            for (int i = 0; i < code.length(); i++) {
                String charStr = String.valueOf(code.charAt(i));
                g.setColor(new Color(20 + random.nextInt(70), 25 + random.nextInt(70), 40 + random.nextInt(80)));

                AffineTransform old = g.getTransform();
                double cx = slot * i + slot / 2.0;
                double cy = height / 2.0;
                // 轻微倾斜，避免旋转过大把字甩出画布
                double angle = (random.nextDouble() - 0.5) * Math.PI / 9;
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
