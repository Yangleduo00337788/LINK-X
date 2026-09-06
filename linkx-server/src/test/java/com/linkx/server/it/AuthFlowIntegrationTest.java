package com.linkx.server.it;

import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 认证核心链路集成测试：注册 → 登录 → 刷新 → 登出吊销。
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REGISTER_CODE = "886688";

    private RegisterDTO registerDTO(String username) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(TEST_PASSWORD);
        dto.setNickname("集成测试-" + username);
        dto.setEmail(username + "@linkx-it.local");
        dto.setEmailCode(REGISTER_CODE);
        return dto;
    }

    private void seedRegisterCode(String email) {
        stringRedisTemplate.opsForValue().set(
                "linkx:register-email:" + email, REGISTER_CODE, Duration.ofMinutes(10));
    }

    private HttpServletRequest mockRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "LinkX-IT/1.0");
        return request;
    }

    private TokenVO registerAndLogin(String username) {
        seedRegisterCode(username + "@linkx-it.local");
        sysUserService.register(registerDTO(username), mockRequest());
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(TEST_PASSWORD);
        return sysUserService.login(loginDTO, "127.0.0.1", "LinkX-IT/1.0", mockRequest());
    }

    @Test
    void contextBootsWithRealMySqlAndRedis() {
        // 上下文启动即代表 Flyway 全量迁移 + 全部基础设施 Bean 初始化成功
        assertThat(sysUserService).isNotNull();
    }

    @Test
    void registerLoginRefreshLogout_fullChain() {
        String username = uniqueName("authit");
        TokenVO tokenVO = registerAndLogin(username);

        // 登录成功：双令牌 + 用户信息
        assertThat(tokenVO.getAccessToken()).isNotBlank();
        assertThat(tokenVO.getRefreshToken()).isNotBlank();
        assertThat(tokenVO.getUser()).isNotNull();
        assertThat(tokenVO.getUser().getUsername()).isEqualTo(username);

        // 刷新：旧 refresh token 换新 access token
        TokenVO refreshed = tokenService.refreshAccessToken(tokenVO.getRefreshToken());
        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getUser().getUsername()).isEqualTo(username);

        // 登出：双令牌吊销
        tokenService.logout(refreshed.getAccessToken(), refreshed.getRefreshToken());

        // 吊销后的 access token 不再通过拦截器的活跃性校验
        assertThatThrownBy(() -> tokenService.assertAccessTokenActive(refreshed.getAccessToken()))
                .isInstanceOf(CustomException.class);
        // 吊销后的 refresh token 不能再换新令牌
        assertThatThrownBy(() -> tokenService.refreshAccessToken(refreshed.getRefreshToken()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void register_duplicateUsernameRejected() {
        String username = uniqueName("dupit");
        registerAndLogin(username);

        seedRegisterCode(username + "@linkx-it.local");
        assertThatThrownBy(() -> sysUserService.register(registerDTO(username), mockRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("注册失败");
    }

    @Test
    void login_wrongPasswordRejected() {
        String username = uniqueName("wrongpw");
        registerAndLogin(username);

        LoginDTO bad = new LoginDTO();
        bad.setUsername(username);
        bad.setPassword("TotallyWrong1");
        assertThatThrownBy(() -> sysUserService.login(bad, "127.0.0.1", "LinkX-IT/1.0", mockRequest()))
                .isInstanceOf(CustomException.class);
    }
}
