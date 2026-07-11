package com.linkx.server.controller;

import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.LogoutDTO;
import com.linkx.server.controller.dto.RefreshTokenDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.vo.CaptchaVO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.service.CaptchaService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
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
        rateLimitService.check(
                "register:" + clientIp(request),
                linkxProperties.getAuth().getRateLimitRegisterPerMinute(),
                60);
        validateCaptchaIfEnabled(registerDTO.getCaptchaId(), registerDTO.getCaptchaCode());
        sysUserService.register(registerDTO);
        return Result.success(null);
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@Validated @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        rateLimitService.check(
                "login:" + clientIp(request),
                linkxProperties.getAuth().getRateLimitLoginPerMinute(),
                60);
        validateCaptchaIfEnabled(loginDTO.getCaptchaId(), loginDTO.getCaptchaCode());
        TokenVO tokenVO = sysUserService.login(loginDTO, clientIp(request), request.getHeader("User-Agent"));
        return Result.success(tokenVO);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Validated @RequestBody RefreshTokenDTO refreshTokenDTO, HttpServletRequest request) {
        rateLimitService.check("refresh:" + clientIp(request), 30, 60);
        return Result.success(tokenService.refreshAccessToken(refreshTokenDTO.getRefreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) LogoutDTO logoutDTO) {
        String refreshToken = logoutDTO != null ? logoutDTO.getRefreshToken() : null;
        tokenService.logout(authorization, refreshToken);
        return Result.success(null);
    }

    private void validateCaptchaIfEnabled(String captchaId, String captchaCode) {
        if (linkxProperties.getAuth().isCaptchaEnabled()) {
            captchaService.validate(captchaId, captchaCode);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
