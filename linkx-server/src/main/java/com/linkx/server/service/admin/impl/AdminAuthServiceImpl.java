package com.linkx.server.service.admin.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
import com.linkx.server.controller.admin.dto.AdminProfileUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminRefreshDTO;
import com.linkx.server.controller.admin.vo.AdminLoginVO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
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
import com.linkx.server.service.admin.AdminAuthService;
import com.linkx.server.service.admin.AdminMenuService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

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

    @Override
    public AdminLoginVO login(AdminLoginDTO dto, HttpServletRequest request, HttpServletResponse response) {
        validateCaptchaIfEnabled(dto.getCaptchaId(), dto.getCaptchaCode());

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(dto.getUsername());
        loginDTO.setPassword(dto.getPassword());
        loginDTO.setCaptchaId(dto.getCaptchaId());
        loginDTO.setCaptchaCode(dto.getCaptchaCode());

        String ip = ClientIpResolver.resolve(request, linkxProperties);
        String userAgent = request.getHeader("User-Agent");

        // 先校验凭证，通过管理员角色后再签发令牌，避免非管理员探测管理端仍获得会话
        SysUser user = sysUserService.verifyCredentials(loginDTO, ip, userAgent, request);
        try {
            assertAdminRole(user.getId());
        } catch (CustomException e) {
            loginAuditService.record(user.getId(), user.getUsername(), ip, userAgent, false, "无管理端访问权限");
            throw e;
        }

        TokenVO tokenVO = sysUserService.establishSession(user, ip, userAgent, request);
        setTokenCookies(response, tokenVO, request);
        return toLoginVO(tokenVO, user.getId());
    }

    @Override
    public AdminUserProfileVO me(Long userId) {
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        assertAdminRole(userId);
        return buildProfile(user);
    }

    @Override
    @Transactional
    public AdminUserProfileVO updateProfile(Long userId, AdminProfileUpdateDTO dto) {
        assertAdminRole(userId);
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
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
            return toLoginVO(tokenVO, userId);
        } catch (CustomException e) {
            tokenCookieUtil.clearTokenCookies(response, isSecure(request));
            throw e;
        }
    }

    private AdminLoginVO toLoginVO(TokenVO tokenVO, Long userId) {
        SysUser user = sysUserMapper.selectOneById(userId);
        long expiresIn = linkxProperties.getJwt().getAccessExpire() / 1000;
        return AdminLoginVO.builder()
                .accessToken(tokenVO.getAccessToken())
                .refreshToken(tokenVO.getRefreshToken())
                .expiresIn(expiresIn)
                .user(buildProfile(user))
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
                .build();
    }

    /**
     * 管理端头像走同源 /media/avatars/{id}，避免浏览器直连 MinIO 预签名地址失败（私有桶 / 跨端口）。
     * 外链头像仍原样返回。
     */
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
        List<String> roles = rbacService.getUserRoleCodes(userId);
        boolean ok = false;
        for (String required : AdminConstants.ADMIN_ROLES) {
            if (roles.contains(required)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new CustomException(403, "无管理端访问权限");
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
