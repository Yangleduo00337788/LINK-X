package com.linkx.server.service.admin.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
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
import com.linkx.server.service.RbacService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import com.linkx.server.service.admin.AdminAuthService;
import com.linkx.server.service.admin.AdminMenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    @Override
    public AdminLoginVO login(AdminLoginDTO dto, HttpServletRequest request, HttpServletResponse response) {
        validateCaptchaIfEnabled(dto.getCaptchaId(), dto.getCaptchaCode());

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(dto.getUsername());
        loginDTO.setPassword(dto.getPassword());
        loginDTO.setCaptchaId(dto.getCaptchaId());
        loginDTO.setCaptchaCode(dto.getCaptchaCode());

        TokenVO tokenVO = sysUserService.login(
                loginDTO,
                ClientIpResolver.resolve(request, linkxProperties),
                request.getHeader("User-Agent"),
                request);

        Long userId = tokenVO.getUser().getId();
        assertAdminRole(userId);

        setTokenCookies(response, tokenVO, request);
        return toLoginVO(tokenVO, userId);
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
                .avatar(user.getAvatar())
                .email(user.getEmail())
                .roles(rbacService.getUserRoleCodes(user.getId()))
                .permissions(new HashSet<>(rbacService.getUserPermissionCodes(user.getId())))
                .build();
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
        if (linkxProperties.getAuth().isCaptchaEnabled()) {
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