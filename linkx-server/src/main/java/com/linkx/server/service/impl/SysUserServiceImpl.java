package com.linkx.server.service.impl;

import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.UserPreference;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.*;
import com.linkx.server.service.EmailService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final String DEFAULT_AVATAR = "/default-avatar.svg";

    private final TokenService tokenService;
    private final LoginAuditService loginAuditService;
    private final RateLimitService rateLimitService;
    private final FileStorageService fileStorageService;
    private final CaptchaService captchaService;
    private final EmailService emailService;
    private final LinkxProperties linkxProperties;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.linkx.server.service.UserPreferenceService userPreferenceService;
    private final DeviceSessionService deviceSessionService;

    @Override
    public void register(RegisterDTO registerDTO, HttpServletRequest request) {
        // IP 级别注册限流
        rateLimitService.checkRegisterRateLimit(request);

        long count = queryChain()
                .where(SysUser::getUsername).eq(registerDTO.getUsername())
                .count();
        if (count > 0) {
            throw new CustomException(400, "注册失败，请检查信息后重试");
        }

        String hashPassword = BCrypt.hashpw(registerDTO.getPassword(), BCrypt.gensalt(12));

        SysUser user = SysUser.builder()
                .username(InputSanitizer.stripHtml(registerDTO.getUsername(), 64))
                .password(hashPassword)
                .nickname(InputSanitizer.sanitizeText(registerDTO.getNickname(), 64))
                .email(InputSanitizer.sanitizeText(registerDTO.getEmail(), 128))
                .avatar(DEFAULT_AVATAR)
                .status(1)
                .build();

        save(user);
    }

    @Override
    public TokenVO login(LoginDTO loginDTO, String ip, String userAgent, HttpServletRequest request) {
        String username = loginDTO.getUsername();

        // 检查账号是否被锁定
        if (rateLimitService.isAccountLocked(username, request)) {
            throw new CustomException(429, "登录失败次数过多，请稍后再试");
        }

        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(username)
                .one();

        // 防御时间侧信道攻击：无论用户是否存在，都执行耗时操作
        String dummyHash = "$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        boolean passwordValid = false;
        if (user != null) {
            passwordValid = BCrypt.checkpw(loginDTO.getPassword(), user.getPassword());
        } else {
            // 用户不存在时执行假校验，消耗相同时间
            BCrypt.checkpw(loginDTO.getPassword(), dummyHash);
        }

        if (user == null || !passwordValid) {
            // 记录失败并检查限流
            rateLimitService.checkLoginRateLimit(username, request);
            loginAuditService.record(null, username, ip, userAgent, false, "用户名或密码错误");
            throw new CustomException(400, "用户名或密码错误");
        }

        // 登录成功后，如果用户的密码哈希 cost < 12，透明升级到 cost=12
        if (passwordNeedsRehash(user.getPassword())) {
            String upgradedHash = BCrypt.hashpw(loginDTO.getPassword(), BCrypt.gensalt(12));
            user.setPassword(upgradedHash);
            updateById(user);
            log.info("已透明升级用户 {} 的密码哈希到 cost=12", username);
        }

        // 用户存在且密码正确，继续检查账号状态
        if (user.getStatus() != 1) {
            loginAuditService.record(user.getId(), username, ip, userAgent, false, "账号已停用");
            throw new CustomException(403, "账号已被停用");
        }

        // 登录成功，清除失败记录
        rateLimitService.clearLoginFailure(username, request);
        loginAuditService.record(user.getId(), username, ip, userAgent, true, "登录成功");

        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = "default-web-device";
        }
        String deviceName = request.getHeader("X-Device-Name");
        String deviceType = request.getHeader("X-Device-Type");
        deviceSessionService.createOrUpdate(
                user.getId(),
                deviceId,
                deviceName != null && !deviceName.isBlank() ? deviceName : "Web 浏览器",
                deviceType != null && !deviceType.isBlank() ? deviceType : "Web",
                ip,
                userAgent);

        return tokenService.issueTokenPair(user, deviceId);
    }

    @Override
    public SysUser updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        boolean updated = false;

        // 更新昵称（HTML 转义防 XSS）
        if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
            user.setNickname(InputSanitizer.sanitizeText(dto.getNickname(), 64));
            updated = true;
        }

        // 更新个性签名（HTML 转义防 XSS）
        if (dto.getSignature() != null) {
            user.setSignature(dto.getSignature().isEmpty()
                    ? ""
                    : InputSanitizer.sanitizeText(dto.getSignature(), 255));
            updated = true;
        }

        if (dto.getGender() != null) {
            user.setGender(dto.getGender().isEmpty() ? null : dto.getGender());
            updated = true;
        }

        if (dto.getBirthday() != null) {
            user.setBirthday(dto.getBirthday());
            updated = true;
        }

        // 国家/省份/地区：从下拉列表/预设值传入，但仍做长度限制防越界
        if (dto.getCountry() != null) {
            user.setCountry(dto.getCountry().isEmpty() ? null
                    : InputSanitizer.sanitizeText(dto.getCountry(), 64));
            updated = true;
        }

        if (dto.getProvince() != null) {
            user.setProvince(dto.getProvince().isEmpty() ? null
                    : InputSanitizer.sanitizeText(dto.getProvince(), 64));
            updated = true;
        }

        if (dto.getRegion() != null) {
            user.setRegion(dto.getRegion().isEmpty() ? null
                    : InputSanitizer.sanitizeText(dto.getRegion(), 64));
            updated = true;
        }

        if (updated) {
            updateById(user);
        }

        return user;
    }

    @Override
    public void updateAvatar(Long userId, String avatarKeyOrUrl) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        // 删除旧头像（如果是有效的对象名/URL）
        String oldAvatar = user.getAvatar();
        if (oldAvatar != null && !oldAvatar.equals(DEFAULT_AVATAR) && !oldAvatar.isEmpty()) {
            try {
                fileStorageService.deleteFile(oldAvatar);
            } catch (Exception e) {
                // 删除失败不影响新头像上传
                log.warn("删除旧头像失败: {}", e.getMessage());
            }
        }

        user.setAvatar(avatarKeyOrUrl);
        updateById(user);
    }

    @Override
    public void updateMomentsBackground(Long userId, String objectKey) {
        UserPreference pref = userPreferenceService.getOrDefault(userId);
        String oldKey = pref.getMomentsBackground();
        if (oldKey != null && !oldKey.isEmpty()) {
            try {
                fileStorageService.deleteFile(oldKey);
            } catch (Exception e) {
                log.warn("删除旧友链背景图失败: {}", e.getMessage());
            }
        }
        pref.setMomentsBackground(objectKey);
        userPreferenceService.upsert(userId, pref);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        // 验证旧密码
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new CustomException(400, "旧密码错误");
        }

        // 新密码加密（cost=12）并保存
        String hashPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        user.setPassword(hashPassword);
        updateById(user);
    }

    @Override
    public void resetPassword(Long userId, String captchaId, String captchaCode, String newPassword) {
        // 强制要求验证码必须开启（生产环境默认 true）
        if (!captchaService.isEnabled()) {
            throw new CustomException(403, "密码重置功能暂时不可用");
        }

        // 验证验证码（原子验证 + ownerId 绑定校验，防止横向越权）
        captchaService.validateForOwner(String.valueOf(userId), captchaId, captchaCode);

        // 通过 userId 直接查找用户（不暴露用户是否存在，统一返回模糊错误）
        SysUser user = getById(userId);
        if (user == null) {
            // 防御：用户不存在时也执行一次假的密码哈希计算
            BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
            throw new CustomException(400, "操作失败，请稍后重试");
        }

        // 新密码加密（cost=12）并保存
        String hashPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        user.setPassword(hashPassword);
        updateById(user);

        // 使该用户的所有现有 refresh token 失效（包括从 Set 中删除）
        tokenService.revokeAllUserTokens(user.getId());

        log.info("用户 {} 重置了密码", user.getUsername());
    }

    @Override
    public String findEmailByUsername(String username) {
        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(username)
                .one();
        return user != null ? user.getEmail() : null;
    }

    @Override
    public void sendPasswordResetEmailCode(String username, String ip) {
        // 限流：每个 IP 每 5 分钟最多请求 5 次
        rateLimitService.check("reset-email:" + ip, 5, 300);

        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(username)
                .one();

        // 无论用户是否存在，都返回成功，防止用户枚举攻击
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("密码重置请求：用户 {} 不存在或未设置邮箱", username);
            // 不抛异常，统一返回成功
            return;
        }

        // 生成 6 位数字验证码（线程安全的 SecureRandom + 显式格式化避免边界值变成 5 位）
        String code = String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1_000_000));

        // 验证码存储到 Redis：key = "linkx:reset-email:{username}", value = "{code}"
        int expireMinutes = linkxProperties.getMail().getCodeExpireMinutes();
        String redisKey = "linkx:reset-email:" + username;
        redisTemplate.opsForValue().set(
                redisKey,
                code,
                java.time.Duration.ofMinutes(expireMinutes)
        );
        // 关键：打印写入的验证码（dev 环境排查用），生产可用 level=WARN 控制
        log.info("[验证码持久化] key='{}', value='{}' (len={}), expireMinutes={}",
                redisKey, code, code.length(), expireMinutes);

        // 发送邮件：捕获更具体的异常类型便于排查
        try {
            emailService.sendPasswordResetCode(user.getEmail(), username, code);
            log.info("密码重置验证码已发送到用户 {} 的邮箱 {}", username, user.getEmail());
        } catch (org.springframework.mail.MailAuthenticationException e) {
            // 授权码错误（最常见问题）
            log.error("发送密码重置邮件失败：QQ 邮箱授权码错误或过期，错误: {}", e.getMessage(), e);
        } catch (org.springframework.mail.MailParseException e) {
            // 邮箱格式非法
            log.error("发送密码重置邮件失败：用户 {} 的邮箱格式非法: {}", username, user.getEmail(), e);
        } catch (org.springframework.mail.MailSendException e) {
            log.error("发送密码重置邮件失败：邮件被服务器拒收/网络异常, 错误: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("发送密码重置邮件失败：未知异常, 错误: {}", e.getMessage(), e);
        }
    }

    @Override
    public void resetPasswordByEmail(String username, String code, String newPassword, String ip) {
        // 限流：每个 IP 每 5 分钟最多尝试 10 次
        rateLimitService.check("reset-verify:" + ip, 10, 300);

        verifyCodeInternal(username, code);

        // 校验通过才进行下一步：重置密码
        doResetPassword(username, newPassword, ip);
    }

    @Override
    public void verifyEmailResetCode(String username, String code, String ip) {
        // 共享 reset-verify 限流桶，避免爆破
        rateLimitService.check("reset-verify:" + ip, 10, 300);
        verifyCodeInternal(username, code);
        // 不删除 key，保留到真正的重置步骤消费
    }

    /**
     * 校验邮箱验证码的内部复用方法。
     * <p>
     * 设计要点：
     *  - 同一个用户名最多允许 N 次错误尝试（防爆破），超过后才真正删除 key；
     *  - 这样用户输入手抖/错误一次不会把正确的验证码吃掉；
     *  - 同时写入 attempts 计数到 Redis，跟 code key 同步 TTL。
     */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private void verifyCodeInternal(String username, String code) {
        String key = "linkx:reset-email:" + username;
        String attemptsKey = "linkx:reset-email:attempts:" + username;

        if (code == null || code.isBlank()) {
            throw new CustomException(400, "验证码不能为空");
        }
        String inputCode = code.trim();

        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            throw new CustomException(400, "验证码错误或已过期，请重新获取");
        }

        if (storedCode.equalsIgnoreCase(inputCode)) {
            // 校验通过：清掉 attempts 计数，不删 code（留给 reset 步骤消费）
            redisTemplate.delete(attemptsKey);
            log.info("[验证码校验] username='{}', match=true", username);
            return;
        }

        // 校验失败：累加错误次数
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        Long ttl = redisTemplate.getExpire(attemptsKey);
        if (ttl == null || ttl < 0) {
            // attempts key 没设过期，初始化时一并设置
            redisTemplate.expire(attemptsKey,
                    java.time.Duration.ofMinutes(linkxProperties.getMail().getCodeExpireMinutes()));
        }
        int remaining = MAX_CODE_ATTEMPTS - attempts.intValue();
        log.info("[验证码校验失败] username='{}', attempts={}, remaining={}", username, attempts, remaining);

        if (attempts >= MAX_CODE_ATTEMPTS) {
            // 错误次数超过上限，吃掉这个验证码（防止爆破）
            redisTemplate.delete(key);
            redisTemplate.delete(attemptsKey);
            throw new CustomException(400, "验证码错误次数过多，已失效，请重新获取");
        }
        // 未到上限：保留 key，让用户继续尝试（保留的还能用！）
        throw new CustomException(400, String.format("验证码错误，还可再尝试 %d 次", Math.max(remaining, 0)));
    }

    /**
     * 重置密码 + 通知的内部复用方法。
     */
    private void doResetPassword(String username, String newPassword, String ip) {
        // 验证成功后强制删除 key（一次性使用）
        redisTemplate.delete("linkx:reset-email:" + username);

        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(username)
                .one();

        if (user == null) {
            BCrypt.hashpw(newPassword, BCrypt.gensalt(12)); // 防时序攻击
            throw new CustomException(400, "操作失败，请稍后重试");
        }

        // 重置密码
        String hashPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        user.setPassword(hashPassword);
        updateById(user);

        // 使所有 refresh token 失效
        tokenService.revokeAllUserTokens(user.getId());

        // 发送密码修改通知邮件
        try {
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                emailService.sendPasswordChangedNotification(user.getEmail(), username, ip);
            }
        } catch (Exception e) {
            log.warn("发送密码修改通知邮件失败: {}", e.getMessage());
        }

        log.info("用户 {} 通过邮箱验证码重置了密码", username);
    }

    @Override
    public void sendBindEmailCode(Long userId, String email, String ip) {
        rateLimitService.check("bind-email:" + ip, 5, 300);
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        String normalized = InputSanitizer.sanitizeText(email, 128).trim().toLowerCase();
        long used = queryChain()
                .where(SysUser::getEmail).eq(normalized)
                .and(SysUser::getId).ne(userId)
                .count();
        if (used > 0) {
            throw new CustomException(400, "该邮箱已被其他账号绑定");
        }
        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        String redisKey = "linkx:bind-email:" + userId;
        int expireMinutes = linkxProperties.getMail().getCodeExpireMinutes();
        redisTemplate.opsForValue().set(redisKey, normalized + "|" + code,
                java.time.Duration.ofMinutes(expireMinutes));
        emailService.sendBindEmailCode(normalized, user.getUsername(), code);
        log.info("已向用户 {} 发送绑定邮箱验证码", user.getUsername());
    }

    @Override
    public void bindEmail(Long userId, String email, String code, String ip) {
        rateLimitService.check("bind-email-verify:" + ip, 10, 300);
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        String normalized = InputSanitizer.sanitizeText(email, 128).trim().toLowerCase();
        String redisKey = "linkx:bind-email:" + userId;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached == null || cached.isBlank()) {
            throw new CustomException(400, "验证码已过期，请重新获取");
        }
        String[] parts = cached.split("\\|", 2);
        if (parts.length != 2 || !parts[0].equalsIgnoreCase(normalized) || !parts[1].equals(code.trim())) {
            throw new CustomException(400, "验证码错误");
        }
        long used = queryChain()
                .where(SysUser::getEmail).eq(normalized)
                .and(SysUser::getId).ne(userId)
                .count();
        if (used > 0) {
            throw new CustomException(400, "该邮箱已被其他账号绑定");
        }
        user.setEmail(normalized);
        updateById(user);
        redisTemplate.delete(redisKey);
        log.info("用户 {} 绑定邮箱成功", user.getUsername());
    }

    @Override
    public void bindPhone(Long userId, String phone, String password) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new CustomException(400, "登录密码错误");
        }
        String normalized = phone.trim();
        long used = queryChain()
                .where(SysUser::getPhone).eq(normalized)
                .and(SysUser::getId).ne(userId)
                .count();
        if (used > 0) {
            throw new CustomException(400, "该手机号已被其他账号绑定");
        }
        user.setPhone(normalized);
        updateById(user);
        log.info("用户 {} 绑定手机号成功", user.getUsername());
    }

    @Override
    public void deleteAccount(Long userId, String password) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new CustomException(400, "登录密码错误");
        }
        user.setStatus(0);
        updateById(user);
        removeById(userId); // 逻辑删除
        tokenService.revokeAllUserTokens(userId);
        log.info("用户 {} 已注销账号", user.getUsername());
    }

    /**
     * 检测 BCrypt 哈希的 cost factor。
     * 格式：$2a$<cost>$<22字符salt><hash>
     * 升级阈值：成本 < 12 时视为需要重新哈希
     */
    private boolean passwordNeedsRehash(String hashed) {
        if (hashed == null || hashed.length() < 7) {
            return false;
        }
        try {
            // 格式 $2a$XX$...
            int firstDollar = hashed.indexOf('$', 1);
            int secondDollar = hashed.indexOf('$', firstDollar + 1);
            if (firstDollar < 0 || secondDollar < 0) {
                return false;
            }
            int cost = Integer.parseInt(hashed.substring(firstDollar + 1, secondDollar));
            return cost < 12;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
