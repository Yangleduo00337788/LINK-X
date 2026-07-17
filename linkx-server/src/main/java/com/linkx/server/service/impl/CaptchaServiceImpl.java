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
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int SIMPLE_CODE_LENGTH = 4;
    private static final int ENHANCED_CODE_LENGTH = 6;

    // 兼容旧代码，使用简单验证码
    @SuppressWarnings("unused")
    private static final int CODE_LENGTH = SIMPLE_CODE_LENGTH;

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
        String code = randomEnhancedCode();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, code, CAPTCHA_TTL);

        return CaptchaVO.builder()
                .captchaId(captchaId)
                .imageBase64(renderEnhancedImageBase64(code))
                .expireSeconds(CAPTCHA_TTL.toSeconds())
                .build();
    }

    @Override
    public CaptchaVO generateForOwner(String ownerId) {
        String code = randomEnhancedCode();
        String captchaId = UUID.randomUUID().toString();
        // 将验证码与 ownerId 绑定存储：key = "linkx:captcha:owner:{captchaId}", value = "{ownerId}:{code}"
        String boundValue = ownerId + ":" + code;
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + "owner:" + captchaId, boundValue, CAPTCHA_TTL);

        return CaptchaVO.builder()
                .captchaId(captchaId)
                .imageBase64(renderEnhancedImageBase64(code))
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
     * 生成增强版验证码：6位字母数字混合
     * 相比简单版，增加大小写混合，提高暴力破解难度
     */
    private String randomEnhancedCode() {
        StringBuilder sb = new StringBuilder(ENHANCED_CODE_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < ENHANCED_CODE_LENGTH; i++) {
            sb.append(ALPHANUMERIC_CHARS.charAt(random.nextInt(ALPHANUMERIC_CHARS.length())));
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

    /**
     * 渲染增强版验证码图片
     * - 6位字符，宽度增加
     * - 添加更多干扰线
     * - 字符随机旋转
     * - 添加背景噪点
     */
    private String renderEnhancedImageBase64(String code) {
        int width = 160;
        int height = 50;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            // 设置抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 渐变背景
            GradientPaint gradient = new GradientPaint(0, 0, new Color(245, 247, 250), width, height, new Color(235, 240, 245));
            g.setPaint(gradient);
            g.fillRect(0, 0, width, height);

            ThreadLocalRandom random = ThreadLocalRandom.current();

            // 添加干扰点（噪点）
            for (int i = 0; i < 100; i++) {
                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200), random.nextInt(100) + 50));
                g.fillOval(random.nextInt(width), random.nextInt(height), random.nextInt(3) + 1, random.nextInt(3) + 1);
            }

            // 添加干扰线
            for (int i = 0; i < 8; i++) {
                g.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180), random.nextInt(80) + 30));
                g.setStroke(new BasicStroke(random.nextFloat() * 1.5f + 0.5f));
                g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }

            // 绘制字符（带旋转）
            g.setFont(new Font("Arial", Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                char c = code.charAt(i);
                String charStr = String.valueOf(c);

                // 随机颜色
                int r = 20 + random.nextInt(80);
                int gr = 20 + random.nextInt(80);
                int b = 20 + random.nextInt(80);
                g.setColor(new Color(r, gr, b));

                // 保存当前变换
                AffineTransform oldTransform = g.getTransform();

                // 随机旋转 (-30° 到 +30°)
                double angle = (random.nextDouble() - 0.5) * Math.PI / 3;
                AffineTransform transform = new AffineTransform();
                transform.rotate(angle, 20 + i * 25 + 12, 28);
                g.setTransform(transform);

                g.drawString(charStr, 20 + i * 25, 33);

                // 恢复变换
                g.setTransform(oldTransform);
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
