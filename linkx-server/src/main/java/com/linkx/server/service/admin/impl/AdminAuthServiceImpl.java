package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.LoginSide;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.security.TotpUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
import com.linkx.server.controller.admin.dto.AdminProfileUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminRefreshDTO;
import com.linkx.server.controller.admin.dto.AdminTotpChallengeDTO;
import com.linkx.server.controller.admin.dto.AdminTotpConfirmDTO;
import com.linkx.server.controller.admin.dto.AdminTotpDisableDTO;
import com.linkx.server.controller.admin.dto.AdminTotpLoginDTO;
import com.linkx.server.controller.admin.vo.AdminLoginVO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminTotpSetupVO;
import com.linkx.server.controller.admin.vo.AdminUserProfileVO;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.CaptchaService;
import com.linkx.server.service.LoginAuditService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import com.linkx.server.service.admin.AdminAccessRiskAssessment;
import com.linkx.server.service.admin.AdminAccessRiskService;
import com.linkx.server.service.admin.AdminAuthService;
import com.linkx.server.service.admin.AdminMenuService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String CHALLENGE_KEY = "linkx:admin:totp:challenge:";
    private static final String SETUP_KEY = "linkx:admin:totp:setup:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final Duration SETUP_TTL = Duration.ofMinutes(10);
    private static final String ISSUER = "LinkX Admin";

    private final SysUserService sysUserService;
    private final SysUserMapper sysUserMapper;
    private final TokenService tokenService;
    private final RbacService rbacService;
    private final CaptchaService captchaService;
    private final AdminMenuService adminMenuService;
    private final TokenCookieUtil tokenCookieUtil;
    private final LinkxProperties linkxProperties;
    private final LoginAuditService loginAuditService;
    private final MediaUrlService mediaUrlService;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;
    private final AdminAccessRiskService adminAccessRiskService;

    @Override
    public AdminLoginVO login(AdminLoginDTO dto, HttpServletRequest request, HttpServletResponse response) {
        String ip = ClientIpResolver.resolve(request, linkxProperties);
        String deviceId = request.getHeader("X-Device-Id");
        AdminAccessRiskAssessment preRisk = adminAccessRiskService.evaluatePreLogin(ip, deviceId);
        try {
            validateCaptchaForLogin(dto.getCaptchaId(), dto.getCaptchaCode(), preRisk);
        } catch (CustomException e) {
            adminAccessRiskService.recordLoginFailure(ip);
            try {
                sysUserService.onLoginFailure(dto.getUsername(), request, LoginSide.ADMIN);
            } catch (CustomException lockEx) {
                throw lockEx;
            }
            throw e;
        }

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(dto.getUsername());
        loginDTO.setPassword(dto.getPassword());
        loginDTO.setCaptchaId(dto.getCaptchaId());
        loginDTO.setCaptchaCode(dto.getCaptchaCode());

        String userAgent = request.getHeader("User-Agent");

        SysUser user;
        try {
            user = sysUserService.verifyCredentials(loginDTO, ip, userAgent, request, LoginSide.ADMIN);
        } catch (CustomException e) {
            adminAccessRiskService.recordLoginFailure(ip);
            throw e;
        }
        try {
            assertAdminRole(user.getId());
        } catch (CustomException e) {
            loginAuditService.record(user.getId(), user.getUsername(), ip, userAgent, false, "无管理端访问权限");
            throw e;
        }

        if (isTotpEnabled(user)) {
            return challengeResponse(user.getId(), true, false);
        }
        if (linkxProperties.getAuth().isAdminTotpRequired()) {
            return challengeResponse(user.getId(), false, true);
        }

        boolean newLoginIp = isNewLoginIp(user.getId(), ip);
        TokenVO tokenVO = sysUserService.establishSession(user, ip, userAgent, request);
        adminAccessRiskService.clearLoginFailures(ip);
        setTokenCookies(response, tokenVO, request);
        AdminLoginVO vo = toLoginVO(tokenVO, user.getId(), ip, newLoginIp);
        adminAccessRiskService.evaluatePostLogin(user.getId(), ip, deviceId, newLoginIp);
        return vo;
    }

    @Override
    public AdminLoginVO verifyTotpLogin(AdminTotpLoginDTO dto, HttpServletRequest request, HttpServletResponse response) {
        Long userId = peekChallenge(dto.getChallengeToken(), false);
        SysUser user = requireUser(userId);
        if (!isTotpEnabled(user) || !StringUtils.hasText(user.getTotpSecret())) {
            throw new CustomException(400, "该账号未启用双因素认证");
        }
        if (!TotpUtils.verify(user.getTotpSecret(), dto.getCode())) {
            failTotp(user, request);
            throw new CustomException(400, "验证码错误");
        }
        redisTemplate.delete(CHALLENGE_KEY + dto.getChallengeToken().trim());
        return issueSession(user, request, response);
    }

    @Override
    public AdminTotpSetupVO beginTotpSetup(Long userId) {
        assertAdminRole(userId);
        SysUser user = requireUser(userId);
        if (isTotpEnabled(user)) {
            throw new CustomException(400, "双因素认证已启用，请先关闭后再重新绑定");
        }
        return storePendingSecret(user);
    }

    @Override
    public AdminTotpSetupVO beginTotpSetupWithChallenge(AdminTotpChallengeDTO dto) {
        Long userId = peekChallenge(dto.getChallengeToken(), true);
        SysUser user = requireUser(userId);
        if (isTotpEnabled(user)) {
            throw new CustomException(400, "双因素认证已启用");
        }
        return storePendingSecret(user);
    }

    @Override
    @Transactional
    public AdminLoginVO confirmTotp(Long userId, AdminTotpConfirmDTO dto,
                                    HttpServletRequest request, HttpServletResponse response) {
        boolean challengeFlow = StringUtils.hasText(dto.getChallengeToken());
        Long effectiveUserId = userId;
        if (challengeFlow) {
            effectiveUserId = peekChallenge(dto.getChallengeToken(), true);
        } else {
            if (userId == null) {
                throw new CustomException(400, "缺少挑战令牌");
            }
            assertAdminRole(userId);
        }

        SysUser user = requireUser(effectiveUserId);
        String pending = redisTemplate.opsForValue().get(SETUP_KEY + effectiveUserId);
        if (!StringUtils.hasText(pending)) {
            throw new CustomException(400, "绑定已过期，请重新开始");
        }
        if (!TotpUtils.verify(pending, dto.getCode())) {
            if (challengeFlow) {
                failTotp(user, request);
            }
            throw new CustomException(400, "验证码错误");
        }

        Date now = new Date();
        user.setTotpSecret(pending);
        user.setTotpEnabled(1);
        user.setTotpConfirmedAt(now);
        user.setUpdateBy(effectiveUserId);
        user.setUpdateTime(now);
        sysUserMapper.update(user);
        redisTemplate.delete(SETUP_KEY + effectiveUserId);
        if (challengeFlow) {
            redisTemplate.delete(CHALLENGE_KEY + dto.getChallengeToken().trim());
            return issueSession(user, request, response);
        }
        return AdminLoginVO.builder()
                .user(buildProfile(sysUserMapper.selectOneById(effectiveUserId)))
                .build();
    }

    @Override
    @Transactional
    public AdminUserProfileVO disableTotp(Long userId, AdminTotpDisableDTO dto) {
        assertAdminRole(userId);
        SysUser user = requireUser(userId);
        if (!isTotpEnabled(user)) {
            throw new CustomException(400, "双因素认证未启用");
        }
        if (linkxProperties.getAuth().isAdminTotpRequired()) {
            throw new CustomException(400, "系统已强制开启双因素认证，无法关闭");
        }
        if (!PasswordEncoderHolder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomException(400, "密码错误");
        }
        if (!TotpUtils.verify(user.getTotpSecret(), dto.getCode())) {
            throw new CustomException(400, "验证码错误");
        }
        user.setTotpEnabled(0);
        user.setTotpSecret(null);
        user.setTotpConfirmedAt(null);
        user.setUpdateBy(userId);
        user.setUpdateTime(new Date());
        // 普通 update 会忽略 null，密钥必须显式清空
        UpdateChain.of(SysUser.class)
                .set(SysUser::getTotpEnabled, 0)
                .set(SysUser::getTotpSecret, (String) null)
                .set(SysUser::getTotpConfirmedAt, (Date) null)
                .set(SysUser::getUpdateBy, userId)
                .set(SysUser::getUpdateTime, new Date())
                .where(SysUser::getId).eq(userId)
                .update();
        redisTemplate.delete(SETUP_KEY + userId);
        return buildProfile(sysUserMapper.selectOneById(userId));
    }

    @Override
    public AdminUserProfileVO me(Long userId) {
        SysUser user = requireUser(userId);
        assertAdminRole(userId);
        return buildProfile(user);
    }

    @Override
    @Transactional
    public AdminUserProfileVO updateProfile(Long userId, AdminProfileUpdateDTO dto) {
        assertAdminRole(userId);
        SysUser user = requireUser(userId);
        if (dto.getNickname() != null) {
            String nickname = InputSanitizer.sanitizeText(dto.getNickname(), 64);
            if (!StringUtils.hasText(nickname)) {
                throw new CustomException(400, "昵称不能为空");
            }
            user.setNickname(nickname);
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar().isBlank() ? null : dto.getAvatar().trim());
        }
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim();
            if (email.isEmpty()) {
                user.setEmail(null);
            } else {
                String normalized = email.toLowerCase();
                long occupied = sysUserMapper.selectCountByQuery(
                        QueryWrapper.create()
                                .where(SysUser::getEmail).eq(normalized)
                                .and(SysUser::getId).ne(userId));
                if (occupied > 0) {
                    throw new CustomException(400, "邮箱已被占用");
                }
                user.setEmail(normalized);
            }
        }
        user.setUpdateBy(userId);
        user.setUpdateTime(new Date());
        sysUserMapper.update(user);
        return buildProfile(sysUserMapper.selectOneById(userId));
    }

    @Override
    public List<AdminMenuTreeVO> menus(Long userId) {
        assertAdminRole(userId);
        return adminMenuService.treeForUser(userId);
    }

    @Override
    public Set<String> permissions(Long userId) {
        assertAdminRole(userId);
        return new HashSet<>(rbacService.getUserPermissionCodes(userId));
    }

    @Override
    public void logout(AdminLogoutDTO dto, String authorization, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = null;
        if (authorization != null && !authorization.isBlank()) {
            accessToken = authorization.startsWith("Bearer ")
                    ? authorization.substring(7)
                    : authorization;
        }
        if (accessToken == null || accessToken.isBlank()) {
            accessToken = tokenCookieUtil.readAccessToken(request);
        }
        String refreshToken = dto != null ? dto.getRefreshToken() : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = tokenCookieUtil.readRefreshToken(request);
        }
        tokenCookieUtil.clearTokenCookies(response, isSecure(request));
        if (accessToken != null && !accessToken.isBlank()) {
            tokenService.logout(accessToken, refreshToken);
        }
    }

    @Override
    public AdminLoginVO refresh(AdminRefreshDTO dto, HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = dto != null ? dto.getRefreshToken() : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = tokenCookieUtil.readRefreshToken(request);
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(400, "缺少刷新令牌");
        }
        try {
            String deviceId = request.getHeader("X-Device-Id");
            TokenVO tokenVO = tokenService.refreshAccessToken(refreshToken, deviceId);
            Long userId = tokenVO.getUser().getId();
            assertAdminRole(userId);
            setTokenCookies(response, tokenVO, request);
            // 刷新令牌不视为新登录，不做新 IP 提示
            return toLoginVO(tokenVO, userId, null, false);
        } catch (CustomException e) {
            tokenCookieUtil.clearTokenCookies(response, isSecure(request));
            throw e;
        }
    }

    private AdminTotpSetupVO storePendingSecret(SysUser user) {
        String secret = TotpUtils.generateSecret();
        redisTemplate.opsForValue().set(SETUP_KEY + user.getId(), secret, SETUP_TTL);
        return AdminTotpSetupVO.builder()
                .secret(secret)
                .otpauthUri(TotpUtils.otpAuthUri(ISSUER, user.getUsername(), secret))
                .build();
    }

    private AdminLoginVO challengeResponse(Long userId, boolean requiresTotp, boolean requiresSetup) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String payload = userId + "|" + (requiresSetup ? "setup" : "verify");
        redisTemplate.opsForValue().set(CHALLENGE_KEY + token, payload, CHALLENGE_TTL);
        return AdminLoginVO.builder()
                .requiresTotp(requiresTotp)
                .requiresTotpSetup(requiresSetup)
                .challengeToken(token)
                .challengeExpiresIn(CHALLENGE_TTL.toSeconds())
                .build();
    }

    private Long peekChallenge(String challengeToken, boolean expectSetup) {
        if (!StringUtils.hasText(challengeToken)) {
            throw new CustomException(400, "缺少挑战令牌");
        }
        String payload = redisTemplate.opsForValue().get(CHALLENGE_KEY + challengeToken.trim());
        return parseChallenge(payload, expectSetup);
    }

    private Long parseChallenge(String payload, boolean expectSetup) {
        if (!StringUtils.hasText(payload)) {
            throw new CustomException(400, "挑战令牌无效或已过期");
        }
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            throw new CustomException(400, "挑战令牌无效");
        }
        String mode = parts[1];
        if (expectSetup && !"setup".equals(mode)) {
            throw new CustomException(400, "当前挑战不支持绑定");
        }
        if (!expectSetup && !"verify".equals(mode)) {
            throw new CustomException(400, "当前挑战不支持验证");
        }
        try {
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "挑战令牌无效");
        }
    }

    private AdminLoginVO issueSession(SysUser user, HttpServletRequest request, HttpServletResponse response) {
        String ip = ClientIpResolver.resolve(request, linkxProperties);
        String userAgent = request.getHeader("User-Agent");
        String deviceId = request.getHeader("X-Device-Id");
        boolean newLoginIp = isNewLoginIp(user.getId(), ip);
        TokenVO tokenVO = sysUserService.establishSession(user, ip, userAgent, request);
        adminAccessRiskService.clearLoginFailures(ip);
        setTokenCookies(response, tokenVO, request);
        AdminLoginVO vo = toLoginVO(tokenVO, user.getId(), ip, newLoginIp);
        adminAccessRiskService.evaluatePostLogin(user.getId(), ip, deviceId, newLoginIp);
        return vo;
    }

    /** 有历史成功登录且当前 IP 不在近期成功 IP 中 → 新 IP。 */
    private boolean isNewLoginIp(Long userId, String ip) {
        String normalized = ClientIpResolver.normalizeToIpv4(ip);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        List<String> recent = loginAuditService.recentSuccessfulIps(userId, 10);
        if (recent == null || recent.isEmpty()) {
            return false;
        }
        for (String known : recent) {
            if (normalized.equalsIgnoreCase(known)) {
                return false;
            }
        }
        return true;
    }

    private void failTotp(SysUser user, HttpServletRequest request) {
        try {
            sysUserService.onLoginFailure(user.getUsername(), request, LoginSide.ADMIN);
        } catch (CustomException lockEx) {
            throw lockEx;
        }
    }

    private boolean isTotpEnabled(SysUser user) {
        return user != null && user.getTotpEnabled() != null && user.getTotpEnabled() == 1;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        return user;
    }

    private AdminLoginVO toLoginVO(TokenVO tokenVO, Long userId, String loginIp, boolean newLoginIp) {
        SysUser user = sysUserMapper.selectOneById(userId);
        long expiresIn = linkxProperties.getJwt().getAccessExpire() / 1000;
        String normalizedIp = StringUtils.hasText(loginIp)
                ? ClientIpResolver.normalizeToIpv4(loginIp)
                : null;
        String apiSignKey = null;
        if (linkxProperties.getSecurity().isApiSignEnabled()) {
            String jti = jwtUtils.getJtiFromToken(tokenVO.getAccessToken());
            apiSignKey = jwtUtils.deriveApiSignKeyHex(jti);
        }
        return AdminLoginVO.builder()
                .accessToken(tokenVO.getAccessToken())
                .refreshToken(tokenVO.getRefreshToken())
                .expiresIn(expiresIn)
                .user(buildProfile(user))
                .apiSignKey(apiSignKey)
                .requiresTotp(false)
                .requiresTotpSetup(false)
                .loginIp(normalizedIp)
                .newLoginIp(newLoginIp)
                .build();
    }

    private AdminUserProfileVO buildProfile(SysUser user) {
        return AdminUserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(toAdminAvatarUrl(user))
                .email(user.getEmail())
                .roles(rbacService.getUserRoleCodes(user.getId()))
                .permissions(new HashSet<>(rbacService.getUserPermissionCodes(user.getId())))
                .totpEnabled(isTotpEnabled(user))
                .build();
    }

    private String toAdminAvatarUrl(SysUser user) {
        if (user == null || !StringUtils.hasText(user.getAvatar())) {
            return null;
        }
        String raw = user.getAvatar().trim();
        if (mediaUrlService.isExternalHttpUrl(raw)
                || raw.startsWith("data:")
                || raw.startsWith("blob:")
                || raw.startsWith("/")) {
            return mediaUrlService.resolveAvatar(raw);
        }
        long v = user.getUpdateTime() != null ? user.getUpdateTime().getTime() : 0L;
        return "/media/avatars/" + user.getId() + "?v=" + v;
    }

    private void assertAdminRole(Long userId) {
        if (!AdminConstants.hasAdminPortalRole(rbacService.getUserRoleCodes(userId))) {
            throw new CustomException(403, "无管理端访问权限");
        }
    }

    private void validateCaptchaForLogin(String captchaId, String captchaCode, AdminAccessRiskAssessment risk) {
        boolean required = linkxProperties.getAuth().isAdminCaptchaEnabled() || risk.isRequireCaptcha();
        if (required) {
            captchaService.validate(captchaId, captchaCode);
        }
    }

    private void validateCaptchaIfEnabled(String captchaId, String captchaCode) {
        if (linkxProperties.getAuth().isAdminCaptchaEnabled()) {
            captchaService.validate(captchaId, captchaCode);
        }
    }

    private void setTokenCookies(HttpServletResponse response, TokenVO tokenVO, HttpServletRequest request) {
        long accessMaxAgeSec = linkxProperties.getJwt().getAccessExpire() / 1000;
        long refreshMaxAgeSec = linkxProperties.getJwt().getRefreshExpire() / 1000;
        tokenCookieUtil.setTokenCookies(response,
                tokenVO.getAccessToken(), tokenVO.getRefreshToken(),
                accessMaxAgeSec, refreshMaxAgeSec, isSecure(request));
    }

    private boolean isSecure(HttpServletRequest request) {
        return linkxProperties.getSecurity().isRequireHttps() || request.isSecure();
    }
}
