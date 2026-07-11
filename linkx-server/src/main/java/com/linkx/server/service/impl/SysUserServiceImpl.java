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
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final String LOGIN_FAIL_PREFIX = "linkx:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "linkx:login:lock:";
    private static final String DEFAULT_AVATAR = "/default-avatar.svg";

    private final TokenService tokenService;
    private final LoginAuditService loginAuditService;
    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;

    @Override
    public void register(RegisterDTO registerDTO) {
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
    public TokenVO login(LoginDTO loginDTO, String ip, String userAgent) {
        String username = loginDTO.getUsername();
        assertAccountNotLocked(username);

        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(username)
                .one();

        if (user == null || !BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            recordLoginFailure(username);
            loginAuditService.record(null, username, ip, userAgent, false, "用户名或密码错误");
            throw new CustomException(400, "用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            loginAuditService.record(user.getId(), username, ip, userAgent, false, "账号已停用");
            throw new CustomException(403, "账号已被停用");
        }

        clearLoginFailure(username);
        loginAuditService.record(user.getId(), username, ip, userAgent, true, "登录成功");
        return tokenService.issueTokenPair(user);
    }

    private void assertAccountNotLocked(String username) {
        String lockKey = LOGIN_LOCK_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new CustomException(429, "登录失败次数过多，请稍后再试");
        }
    }

    private void recordLoginFailure(String username) {
        String failKey = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, Duration.ofMinutes(linkxProperties.getAuth().getLockDurationMinutes()));
        }
        if (count != null && count >= linkxProperties.getAuth().getLoginMaxAttempts()) {
            redisTemplate.opsForValue().set(
                    LOGIN_LOCK_PREFIX + username,
                    "1",
                    Duration.ofMinutes(linkxProperties.getAuth().getLockDurationMinutes()));
            redisTemplate.delete(failKey);
        }
    }

    private void clearLoginFailure(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
        redisTemplate.delete(LOGIN_LOCK_PREFIX + username);
    }
}
