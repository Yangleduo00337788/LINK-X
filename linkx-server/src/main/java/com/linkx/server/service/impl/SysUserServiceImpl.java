package com.linkx.server.service.impl;

import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.*;
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
        return tokenService.issueTokenPair(user);
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
    public void resetPassword(String username, String captchaId, String captchaCode, String newPassword) {
        // 验证验证码
        captchaService.validate(captchaId, captchaCode);

        // 查找用户
        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(username)
                .one();
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        // 新密码加密（cost=12）并保存
        String hashPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        user.setPassword(hashPassword);
        updateById(user);
        log.info("用户 {} 通过验证码重置了密码", username);
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
