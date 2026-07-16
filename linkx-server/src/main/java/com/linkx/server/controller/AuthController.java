package com.linkx.server.controller;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.LogoutDTO;
import com.linkx.server.controller.dto.RefreshTokenDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.ResetPasswordDTO;
import com.linkx.server.controller.vo.CaptchaVO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.CaptchaService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final RateLimitService rateLimitService;
    private final LinkxProperties linkxProperties;

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO registerDTO, HttpServletRequest request) {
        validateCaptchaIfEnabled(registerDTO.getCaptchaId(), registerDTO.getCaptchaCode());
        sysUserService.register(registerDTO, request);
        return Result.success(null);
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@Validated @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        validateCaptchaIfEnabled(loginDTO.getCaptchaId(), loginDTO.getCaptchaCode());
        TokenVO tokenVO = sysUserService.login(loginDTO, clientIp(request), request.getHeader("User-Agent"), request);
        return Result.success(tokenVO);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Validated @RequestBody RefreshTokenDTO refreshTokenDTO, HttpServletRequest request) {
        rateLimitService.check("refresh:" + clientIp(request), 30, 60);
        try {
            return Result.success(tokenService.refreshAccessToken(refreshTokenDTO.getRefreshToken()));
        } catch (CustomException e) {
            // 失败时递增 IP 维度的失败计数（防止暴力枚举 refresh token）
            try {
                rateLimitService.recordRefreshFailure(request);
            } catch (CustomException rateLimitEx) {
                // 达到锁定阈值时直接抛出 429
                throw rateLimitEx;
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) LogoutDTO logoutDTO) {
        // 必须携带 Authorization 头（accessToken），否则拒绝，防止任意 refreshToken 即可吊销账号
        if (authorization == null || authorization.isBlank()) {
            throw new com.linkx.server.exception.CustomException(401, "未提供访问令牌");
        }
        String accessToken = authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
        String refreshToken = logoutDTO != null ? logoutDTO.getRefreshToken() : null;
        tokenService.logout(accessToken, refreshToken);
        return Result.success(null);
    }

    /**
     * 找回密码
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        rateLimitService.check("reset-password:" + dto.getUsername(), 3, 300);
        sysUserService.resetPassword(dto.getUsername(), dto.getCaptchaId(), dto.getCaptchaCode(), dto.getNewPassword());
        return Result.success(null);
    }

    private void validateCaptchaIfEnabled(String captchaId, String captchaCode) {
        if (linkxProperties.getAuth().isCaptchaEnabled()) {
            captchaService.validate(captchaId, captchaCode);
        }
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, linkxProperties);
    }
}
