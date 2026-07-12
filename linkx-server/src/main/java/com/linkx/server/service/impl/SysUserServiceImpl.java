package com.linkx.server.service.impl;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.LoginAuditService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final String DEFAULT_AVATAR = "/default-avatar.svg";

    private final TokenService tokenService;
    private final LoginAuditService loginAuditService;
    private final RateLimitService rateLimitService;

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

        String hashPassword = BCrypt.hashpw(registerDTO.getPassword(), BCrypt.gensalt());

        SysUser user = SysUser.builder()
                .username(registerDTO.getUsername())
                .password(hashPassword)
                .nickname(registerDTO.getNickname())
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
}
