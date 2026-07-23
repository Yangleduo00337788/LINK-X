package com.linkx.server.service;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 鉴权异常路径：无 token / 伪造 token / 过期 token / 登出与吊销。
 */
@DisplayName("鉴权异常路径测试")
class ExceptionPathTest extends BaseIntegrationTest {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    @Nested
    @DisplayName("未授权访问")
    class Unauthorized {

        @Test
        @DisplayName("不带 Authorization 访问业务接口应返回 401")
        void noToken_returns401() throws Exception {
            mockMvc.perform(get("/chat/sessions"))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("空 Bearer 应返回 401")
        void emptyBearer_returns401() throws Exception {
            mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", "Bearer "))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("伪造 access token 应返回 401")
        void fakeToken_returns401() throws Exception {
            String fake = "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiYWNjZXNzIiwidXNlcklkIjoxLCJ0eXAiOiJhY2Nlc3MiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMDAwMH0.invalid";
            mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", "Bearer " + fake))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("明显非法字符串 token 应返回 401")
        void garbageToken_returns401() throws Exception {
            mockMvc.perform(get("/user/devices")
                            .header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("过期与吊销")
    class ExpiredAndRevoked {

        @Test
        @DisplayName("已过期 JWT 访问应返回 401")
        void expiredJwt_returns401() throws Exception {
            String expired = jwtUtils.generateToken(1L, "expired_user", TokenType.ACCESS, "jti-expired", -1000L);
            mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", "Bearer " + expired))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("登出后 access token 应被 TokenService 判定失效")
        void afterLogout_tokenInactive() throws Exception {
            TestUser user = registerAndLogin("expath");
            mockMvc.perform(post("/auth/logout")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(jsonPath("$.code").value(200));

            assertThrows(CustomException.class,
                    () -> tokenService.assertAccessTokenActive(user.accessToken));
        }

        @Test
        @DisplayName("revokeAllUserTokens 后 access token 应失效")
        void revokeAll_tokenInactive() {
            TestUser user = registerAndLogin("revokeall");
            tokenService.revokeAllUserTokens(user.userId);

            assertThrows(CustomException.class,
                    () -> tokenService.assertAccessTokenActive(user.accessToken));
        }
    }
}
