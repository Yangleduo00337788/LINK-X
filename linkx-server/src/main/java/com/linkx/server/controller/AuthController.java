package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.Result;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.LogoutDTO;
import com.linkx.server.controller.dto.RefreshTokenDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.ResetPasswordDTO;
import com.linkx.server.controller.dto.ResetPasswordByEmailRequest;
import com.linkx.server.controller.dto.SendRegisterCodeRequest;
import com.linkx.server.controller.dto.SendResetCodeRequest;
import com.linkx.server.controller.dto.VerifyResetCodeRequest;
import com.linkx.server.controller.vo.AuthConfigVO;
import com.linkx.server.controller.vo.CaptchaVO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.CaptchaService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "${openapi.tag.auth}")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final RateLimitService rateLimitService;
    private final LinkxProperties linkxProperties;
    private final TokenCookieUtil tokenCookieUtil;

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    /** 匿名可读：客户端据此隐藏/展示验证码、注册入口、忘记密码等 */
    @GetMapping("/config")
    public Result<AuthConfigVO> config() {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return Result.success(AuthConfigVO.builder()
                .captchaEnabled(captchaService.isEnabled())
                .registerEnabled(auth.isRegisterEnabled())
                .forgotPasswordEmailEnabled(auth.isForgotPasswordEmailEnabled())
                .passwordPolicy(AuthConfigVO.PasswordPolicy.builder()
                        .minLength(auth.getPasswordMinLength())
                        .maxLength(auth.getPasswordMaxLength())
                        .requireUpperLower(auth.isPasswordRequireUpperLower())
                        .requireDigit(auth.isPasswordRequireDigit())
                        .requireSpecial(auth.isPasswordRequireSpecial())
                        .build())
                .build());
    }

    @PostMapping("/send-register-code")
    public Result<Void> sendRegisterCode(@Valid @RequestBody SendRegisterCodeRequest body,
                                         HttpServletRequest request) {
        if (!linkxProperties.getAuth().isRegisterEnabled()) {
            throw new CustomException(403, "当前未开放注册");
        }
        rateLimitService.check("send-register:" + clientIp(request), 3, 60);
        sysUserService.sendRegisterEmailCode(body.getEmail(), body.getUsername(), clientIp(request));
        return Result.success(null);
    }

    @AuditAction(operationType = "REGISTER", description = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO, HttpServletRequest request) {
        if (!linkxProperties.getAuth().isRegisterEnabled()) {
            throw new CustomException(403, "当前未开放注册");
        }
        validateCaptchaIfEnabled(registerDTO.getCaptchaId(), registerDTO.getCaptchaCode());
        sysUserService.register(registerDTO, request);
        return Result.success(null);
    }

    @AuditAction(operationType = "LOGIN", description = "用户登录")
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO loginDTO,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        try {
            validateCaptchaIfEnabled(loginDTO.getCaptchaId(), loginDTO.getCaptchaCode());
        } catch (CustomException e) {
            // 验证码错误也计入登录失败次数，达到阈值同样封禁
            try {
                sysUserService.onLoginFailure(loginDTO.getUsername(), request, com.linkx.server.common.LoginSide.CLIENT);
            } catch (CustomException lockEx) {
                throw lockEx;
            }
            throw e;
        }
        TokenVO tokenVO = sysUserService.login(loginDTO, clientIp(request), request.getHeader("User-Agent"), request);
        // Web 环境：通过 HttpOnly + Secure + SameSite=Lax Cookie 下发 token，避免 XSS 窃取；
        // Electron 环境忽略 Cookie，仍走 Authorization Header + safeStorage 落盘。
        setTokenCookies(response, tokenVO, request);
        return Result.success(tokenVO);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestBody(required = false) @jakarta.validation.Valid RefreshTokenDTO refreshTokenDTO,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        rateLimitService.check("refresh:" + clientIp(request), 30, 60);
        // Web 环境 refreshToken 在 HttpOnly Cookie 中；Electron 在请求体中。两者兼容：请求体优先，Cookie 兜底。
        String refreshToken = (refreshTokenDTO != null && refreshTokenDTO.getRefreshToken() != null
                && !refreshTokenDTO.getRefreshToken().isBlank())
                ? refreshTokenDTO.getRefreshToken()
                : tokenCookieUtil.readRefreshToken(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(400, "缺少刷新令牌");
        }
        try {
            String deviceId = request.getHeader("X-Device-Id");
            TokenVO tokenVO = tokenService.refreshAccessToken(refreshToken, deviceId);
            // 刷新成功：重新下发 Cookie，续期 HttpOnly Cookie 中的 token
            setTokenCookies(response, tokenVO, request);
            return Result.success(tokenVO);
        } catch (CustomException e) {
            // 刷新失败：清除 Cookie，避免前端持有失效 Cookie 反复重试
            tokenCookieUtil.clearTokenCookies(response, isSecure(request));
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
            @RequestBody(required = false) LogoutDTO logoutDTO,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Access Token 读取顺序：Authorization Header（Electron）优先，Cookie（Web）兜底
        String accessToken = null;
        if (authorization != null && !authorization.isBlank()) {
            accessToken = authorization.startsWith("Bearer ")
                    ? authorization.substring(7)
                    : authorization;
        }
        if (accessToken == null || accessToken.isBlank()) {
            accessToken = tokenCookieUtil.readAccessToken(request);
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new com.linkx.server.exception.CustomException(401, "未提供访问令牌");
        }
        // Refresh Token 读取顺序：请求体优先，Cookie 兜底
        String refreshToken = (logoutDTO != null) ? logoutDTO.getRefreshToken() : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = tokenCookieUtil.readRefreshToken(request);
        }
        // 先清 Cookie（Web 环境登出的关键动作），再做后端 token 吊销
        tokenCookieUtil.clearTokenCookies(response, isSecure(request));
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
        if (!linkxProperties.getAuth().isForgotPasswordEmailEnabled()) {
            throw new CustomException(403, "忘记密码邮箱验证未启用");
        }
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
        if (!linkxProperties.getAuth().isForgotPasswordEmailEnabled()) {
            throw new CustomException(403, "忘记密码邮箱验证未启用");
        }
        // 防重置码爆破：同 IP 5 分钟最多 10 次
        rateLimitService.check("reset-by-email:" + clientIp(httpRequest), 10, 300);
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
        if (!linkxProperties.getAuth().isForgotPasswordEmailEnabled()) {
            throw new CustomException(403, "忘记密码邮箱验证未启用");
        }
        // 防重置码枚举：同 IP 5 分钟最多 20 次
        rateLimitService.check("verify-reset:" + clientIp(httpRequest), 20, 300);
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

    /**
     * 下发 token Cookie 到响应。Max-Age 取自 linkx.jwt.*-expire（毫秒转秒）。
     */
    private void setTokenCookies(HttpServletResponse response, TokenVO tokenVO, HttpServletRequest request) {
        long accessMaxAgeSec = linkxProperties.getJwt().getAccessExpire() / 1000;
        long refreshMaxAgeSec = linkxProperties.getJwt().getRefreshExpire() / 1000;
        tokenCookieUtil.setTokenCookies(response,
                tokenVO.getAccessToken(), tokenVO.getRefreshToken(),
                accessMaxAgeSec, refreshMaxAgeSec, isSecure(request));
    }

    /**
     * Cookie 是否标记 Secure：require-https 开启或请求本身为 HTTPS 时设 Secure，
     * 本地 HTTP 开发不设（否则浏览器会丢弃 Secure Cookie）。
     */
    private boolean isSecure(HttpServletRequest request) {
        return linkxProperties.getSecurity().isRequireHttps() || request.isSecure();
    }
}
