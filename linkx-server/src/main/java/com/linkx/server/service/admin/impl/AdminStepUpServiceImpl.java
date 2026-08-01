package com.linkx.server.service.admin.impl;

import com.linkx.server.common.security.TotpUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminStepUpRequestDTO;
import com.linkx.server.controller.admin.dto.AdminStepUpVerifyDTO;
import com.linkx.server.controller.admin.vo.AdminStepUpChallengeVO;
import com.linkx.server.controller.admin.vo.AdminStepUpTokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.EmailService;
import com.linkx.server.service.admin.AdminStepUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStepUpServiceImpl implements AdminStepUpService {

    public static final String HEADER = "X-Step-Up-Token";
    public static final int CODE_STEP_UP_REQUIRED = 428;

    private static final String EMAIL_CODE_KEY = "linkx:admin:stepup:email:";
    private static final String EMAIL_ATTEMPTS_KEY = "linkx:admin:stepup:email:attempts:";
    private static final String TOKEN_KEY = "linkx:admin:stepup:token:";
    private static final String RATE_KEY = "linkx:admin:stepup:rate:";
    private static final Duration EMAIL_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration RATE_TTL = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final LinkxProperties linkxProperties;

    @Override
    public boolean isEnabled() {
        return linkxProperties.getAuth().isAdminStepUpEnabled();
    }

    @Override
    public AdminStepUpChallengeVO options(Long userId, String action) {
        SysUser user = requireUser(userId);
        return buildOptions(user, action, null, null);
    }

    @Override
    public AdminStepUpChallengeVO request(Long userId, AdminStepUpRequestDTO dto) {
        SysUser user = requireUser(userId);
        String method = normalizeMethod(dto.getMethod());
        String action = normalizeAction(dto.getAction());
        assertMethodAvailable(user, method);
        hitRateLimit(userId);

        if ("email".equals(method)) {
            String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
            redisTemplate.opsForValue().set(EMAIL_CODE_KEY + userId, code + "|" + action, EMAIL_TTL);
            redisTemplate.delete(EMAIL_ATTEMPTS_KEY + userId);
            emailService.sendAdminStepUpCode(user.getEmail().trim(), user.getUsername(), code);
            return buildOptions(user, action, method, EMAIL_TTL.toSeconds());
        }
        if ("totp".equals(method)) {
            // TOTP 无需预发码
            return buildOptions(user, action, method, null);
        }
        // sms 预留
        throw new CustomException(400, "短信二次验证尚未配置短信服务商");
    }

    @Override
    public AdminStepUpTokenVO verify(Long userId, AdminStepUpVerifyDTO dto) {
        SysUser user = requireUser(userId);
        String method = normalizeMethod(dto.getMethod());
        String action = normalizeAction(dto.getAction());
        String code = dto.getCode() == null ? "" : dto.getCode().trim();
        assertMethodAvailable(user, method);
        hitRateLimit(userId);

        if ("totp".equals(method)) {
            if (!TotpUtils.verify(user.getTotpSecret(), code)) {
                throw new CustomException(400, "TOTP 验证码错误");
            }
        } else if ("email".equals(method)) {
            verifyEmailCode(userId, action, code);
        } else {
            throw new CustomException(400, "短信二次验证尚未配置短信服务商");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        // payload: userId|action|method
        String payload = userId + "|" + action + "|" + method;
        redisTemplate.opsForValue().set(TOKEN_KEY + token, payload, TOKEN_TTL);
        return AdminStepUpTokenVO.builder()
                .stepUpToken(token)
                .action(action)
                .method(method)
                .expiresIn(TOKEN_TTL.toSeconds())
                .build();
    }

    @Override
    public boolean consumeToken(Long userId, String token, String action) {
        if (!StringUtils.hasText(token) || userId == null || !StringUtils.hasText(action)) {
            return false;
        }
        String key = TOKEN_KEY + token.trim();
        String payload = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 2) {
            return false;
        }
        if (!String.valueOf(userId).equals(parts[0])) {
            return false;
        }
        String scope = parts[1];
        if (!"*".equals(scope) && !action.equals(scope)) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    private void verifyEmailCode(Long userId, String action, String code) {
        String raw = redisTemplate.opsForValue().get(EMAIL_CODE_KEY + userId);
        if (!StringUtils.hasText(raw)) {
            throw new CustomException(400, "邮箱验证码已过期，请重新获取");
        }
        String attemptsKey = EMAIL_ATTEMPTS_KEY + userId;
        long attempts = incr(attemptsKey, EMAIL_TTL);
        if (attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(EMAIL_CODE_KEY + userId);
            throw new CustomException(429, "验证失败次数过多，请重新获取验证码");
        }
        String[] parts = raw.split("\\|", 2);
        String expected = parts[0];
        String boundAction = parts.length > 1 ? parts[1] : "";
        if (!action.equals(boundAction) || !constantTimeEquals(expected, code)) {
            throw new CustomException(400, "邮箱验证码错误");
        }
        redisTemplate.delete(EMAIL_CODE_KEY + userId);
        redisTemplate.delete(attemptsKey);
    }

    private void assertMethodAvailable(SysUser user, String method) {
        List<String> methods = availableMethods(user);
        if (!methods.contains(method)) {
            if ("sms".equals(method)) {
                throw new CustomException(400, "短信二次验证尚未配置短信服务商");
            }
            if ("totp".equals(method)) {
                throw new CustomException(400, "请先在个人中心启用 TOTP");
            }
            if ("email".equals(method)) {
                throw new CustomException(400, "请先绑定管理员邮箱");
            }
            throw new CustomException(400, "不支持的验证方式");
        }
    }

    private AdminStepUpChallengeVO buildOptions(SysUser user, String action, String method, Long expiresIn) {
        return AdminStepUpChallengeVO.builder()
                .methods(availableMethods(user))
                .totpEnabled(isTotpEnabled(user))
                .emailBound(hasEmail(user))
                .emailMasked(maskEmail(user.getEmail()))
                .smsAvailable(false)
                .action(action)
                .method(method)
                .expiresIn(expiresIn)
                .build();
    }

    private List<String> availableMethods(SysUser user) {
        List<String> methods = new ArrayList<>(2);
        if (isTotpEnabled(user)) {
            methods.add("totp");
        }
        if (hasEmail(user)) {
            methods.add("email");
        }
        return methods;
    }

    private void hitRateLimit(Long userId) {
        String key = RATE_KEY + userId;
        long n = incr(key, RATE_TTL);
        if (n > MAX_REQUESTS_PER_MINUTE) {
            throw new CustomException(429, "二次验证请求过于频繁，请稍后再试");
        }
    }

    private long incr(String key, Duration ttl) {
        Long n = redisTemplate.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return n == null ? 0L : n;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        return user;
    }

    private static boolean isTotpEnabled(SysUser user) {
        return user.getTotpEnabled() != null
                && user.getTotpEnabled() == 1
                && StringUtils.hasText(user.getTotpSecret());
    }

    private static boolean hasEmail(SysUser user) {
        return StringUtils.hasText(user.getEmail()) && user.getEmail().contains("@");
    }

    private static String normalizeMethod(String method) {
        if (!StringUtils.hasText(method)) {
            throw new CustomException(400, "验证方式不能为空");
        }
        return method.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new CustomException(400, "动作标识不能为空");
        }
        return action.trim();
    }

    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return null;
        }
        String[] parts = email.trim().split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.substring(0, 2) + "***@" + domain;
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
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }
}
