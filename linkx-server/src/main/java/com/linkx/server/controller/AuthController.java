package com.linkx.server.controller;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.LogoutDTO;
import com.linkx.server.controller.dto.RefreshTokenDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.ResetPasswordDTO;
import com.linkx.server.controller.dto.ResetPasswordByEmailRequest;
import com.linkx.server.controller.dto.SendResetCodeRequest;
import com.linkx.server.controller.dto.VerifyResetCodeRequest;
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

    @AuditAction(operationType = "REGISTER", description = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO, HttpServletRequest request) {
        validateCaptchaIfEnabled(registerDTO.getCaptchaId(), registerDTO.getCaptchaCode());
        sysUserService.register(registerDTO, request);
        return Result.success(null);
    }

    @AuditAction(operationType = "LOGIN", description = "用户登录")
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        validateCaptchaIfEnabled(loginDTO.getCaptchaId(), loginDTO.getCaptchaCode());
        TokenVO tokenVO = sysUserService.login(loginDTO, clientIp(request), request.getHeader("User-Agent"), request);
        return Result.success(tokenVO);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshTokenDTO refreshTokenDTO, HttpServletRequest request) {
        rateLimitService.check("refresh:" + clientIp(request), 30, 60);
        try {
            String deviceId = request.getHeader("X-Device-Id");
            return Result.success(tokenService.refreshAccessToken(refreshTokenDTO.getRefreshToken(), deviceId));
        } catch (CustomException e) {
            try {
                rateLimitService.recordRefreshFailure(request);
            } catch (CustomException rateLimitEx) {
                throw rateLimitEx;
            }
            throw e;
        }
    }

    @AuditAction(operationType = "LOGOUT", description = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) LogoutDTO logoutDTO) {
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

    @AuditAction(operationType = "RESET_PASSWORD", description = "重置密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(
            @Valid @RequestBody ResetPasswordDTO dto,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        rateLimitService.check("reset-password:" + clientIp(request), 3, 300);
        sysUserService.resetPassword(userId, dto.getCaptchaId(), dto.getCaptchaCode(), dto.getNewPassword());
        return Result.success(null);
    }

    /**
     * 生成重置密码专用验证码（与当前登录账号绑定，防横向越权）。
     * 需要已登录，验证码绑定到 token 中的 userId。
     */
    @PostMapping("/reset-password-captcha")
    public Result<CaptchaVO> resetPasswordCaptcha(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        rateLimitService.check("reset-captcha:" + userId, 3, 60);
        return Result.success(captchaService.generateForOwner(String.valueOf(userId)));
    }

    /**
     * 发送密码重置邮件验证码
     * 用户输入用户名，系统查找该用户的邮箱并发送验证码
     */
    @PostMapping("/send-reset-code")
    public Result<Void> sendResetCode(
            @Valid @RequestBody SendResetCodeRequest request,
            HttpServletRequest httpRequest) {
        rateLimitService.check("send-reset:" + clientIp(httpRequest), 3, 60);
        sysUserService.sendPasswordResetEmailCode(request.getUsername(), clientIp(httpRequest));
        return Result.success(null);
    }

    /**
     * 通过邮箱验证码重置密码
     */
    @PostMapping("/reset-password-by-email")
    public Result<Void> resetPasswordByEmail(
            @Valid @RequestBody ResetPasswordByEmailRequest request,
            HttpServletRequest httpRequest) {
        sysUserService.resetPasswordByEmail(
                request.getUsername(),
                request.getCode(),
                request.getNewPassword(),
                clientIp(httpRequest)
        );
        return Result.success(null);
    }

    /**
     * 仅校验邮箱验证码，不消费。
     * 提供给前端在进入「重置密码」表单前先校验，避免用户填好新密码后才发现验证码错了。
     */
    @PostMapping("/verify-reset-code")
    public Result<Void> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest request,
            HttpServletRequest httpRequest) {
        sysUserService.verifyEmailResetCode(
                request.getUsername(),
                request.getCode(),
                clientIp(httpRequest)
        );
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
