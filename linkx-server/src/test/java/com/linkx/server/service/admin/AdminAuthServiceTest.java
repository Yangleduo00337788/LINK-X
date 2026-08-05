package com.linkx.server.service.admin;

import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.common.security.TotpUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
import com.linkx.server.controller.admin.dto.AdminProfileUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminRefreshDTO;
import com.linkx.server.controller.admin.dto.AdminTotpChallengeDTO;
import com.linkx.server.controller.admin.dto.AdminTotpConfirmDTO;
import com.linkx.server.controller.admin.dto.AdminTotpDisableDTO;
import com.linkx.server.controller.admin.dto.AdminTotpLoginDTO;
import com.linkx.server.controller.admin.vo.AdminLoginVO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminUserProfileVO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.CaptchaService;
import com.linkx.server.service.LoginAuditService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.TokenService;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.service.admin.impl.AdminAuthServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminAuthService 管理端认证")
class AdminAuthServiceTest {

    @Mock SysUserService sysUserService;
    @Mock SysUserMapper sysUserMapper;
    @Mock TokenService tokenService;
    @Mock RbacService rbacService;
    @Mock CaptchaService captchaService;
    @Mock AdminMenuService adminMenuService;
    @Mock TokenCookieUtil tokenCookieUtil;
    @Mock LoginAuditService loginAuditService;
    @Mock MediaUrlService mediaUrlService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock JwtUtils jwtUtils;
    @Mock AdminAccessRiskService adminAccessRiskService;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    private LinkxProperties linkxProperties;
    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getAuth().setAdminCaptchaEnabled(false);
        linkxProperties.getAuth().setAdminTotpRequired(false);
        linkxProperties.getJwt().setAccessExpire(1_800_000L);
        linkxProperties.getJwt().setRefreshExpire(259_200_000L);
        linkxProperties.getSecurity().setRequireHttps(false);
        linkxProperties.getSecurity().setApiSignEnabled(false);

        when(adminAccessRiskService.evaluatePreLogin(any(), any()))
                .thenReturn(AdminAccessRiskAssessment.none());
        when(adminAccessRiskService.evaluatePostLogin(any(), any(), any(), anyBoolean()))
                .thenReturn(AdminAccessRiskAssessment.none());

        service = new AdminAuthServiceImpl(
                sysUserService, sysUserMapper, tokenService, rbacService, captchaService,
                adminMenuService, tokenCookieUtil, linkxProperties, loginAuditService,
                mediaUrlService, redisTemplate, jwtUtils,
                adminAccessRiskService);
    }

    private SysUser adminUser(Long id) {
        return SysUser.builder()
                .id(id)
                .username("admin")
                .nickname("Admin")
                .email("admin@test.com")
                .password(PasswordEncoderHolder.encode("Admin1234"))
                .totpEnabled(0)
                .updateTime(new Date())
                .build();
    }

    private TokenVO tokenFor(Long userId) {
        return TokenVO.builder()
                .accessToken("access-" + userId)
                .refreshToken("refresh-" + userId)
                .user(UserInfoVO.builder().id(userId).username("admin").build())
                .build();
    }

    private void stubAdminRole(Long userId) {
        when(rbacService.getUserRoleCodes(userId)).thenReturn(List.of("admin"));
        when(rbacService.getUserPermissionCodes(userId)).thenReturn(List.of("admin:read"));
    }

    private void stubRequest() {
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.isSecure()).thenReturn(false);
    }

    private static String totpCodeNow(String secret) throws Exception {
        Method decode = TotpUtils.class.getDeclaredMethod("decodeBase32", String.class);
        decode.setAccessible(true);
        byte[] key = (byte[]) decode.invoke(null, secret);
        Method gen = TotpUtils.class.getDeclaredMethod("generateCode", byte[].class, long.class);
        gen.setAccessible(true);
        long counter = System.currentTimeMillis() / 1000 / 30;
        return (String) gen.invoke(null, key, counter);
    }

    @Test
    @DisplayName("登录成功并下发 Token")
    void login_success() {
        SysUser user = adminUser(1L);
        stubAdminRole(1L);
        stubRequest();
        when(sysUserService.verifyCredentials(any(), anyString(), anyString(), eq(request), any()))
                .thenReturn(user);
        when(loginAuditService.recentSuccessfulIps(1L, 10)).thenReturn(List.of("203.0.113.1"));
        when(sysUserService.establishSession(eq(user), anyString(), anyString(), eq(request)))
                .thenReturn(tokenFor(1L));
        when(sysUserMapper.selectOneById(1L)).thenReturn(user);

        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("Admin1234");

        AdminLoginVO vo = service.login(dto, request, response);
        assertEquals("access-1", vo.getAccessToken());
        assertFalse(Boolean.TRUE.equals(vo.getRequiresTotp()));
        assertTrue(Boolean.TRUE.equals(vo.getNewLoginIp()));
        verify(tokenCookieUtil).setTokenCookies(eq(response), anyString(), anyString(), anyLong(), anyLong(), eq(false));
    }

    @Test
    @DisplayName("登录验证码失败与无管理端权限")
    void login_captchaAndRoleGuards() {
        linkxProperties.getAuth().setAdminCaptchaEnabled(true);
        doThrow(new CustomException(400, "captcha bad")).when(captchaService).validate(anyString(), anyString());
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("Admin1234");
        dto.setCaptchaId("c1");
        dto.setCaptchaCode("0000");
        assertThrows(CustomException.class, () -> service.login(dto, request, response));
        verify(sysUserService).onLoginFailure(eq("admin"), eq(request), any());

        linkxProperties.getAuth().setAdminCaptchaEnabled(false);
        SysUser user = adminUser(2L);
        stubRequest();
        when(sysUserService.verifyCredentials(any(), anyString(), anyString(), eq(request), any()))
                .thenReturn(user);
        when(rbacService.getUserRoleCodes(2L)).thenReturn(List.of("client"));
        assertThrows(CustomException.class, () -> service.login(dto, request, response));
        verify(loginAuditService).record(eq(2L), eq("admin"), anyString(), anyString(), eq(false), anyString());
    }

    @Test
    @DisplayName("登录 TOTP 挑战")
    void login_totpChallenge() {
        SysUser user = adminUser(3L);
        user.setTotpEnabled(1);
        user.setTotpSecret(TotpUtils.generateSecret());
        stubAdminRole(3L);
        stubRequest();
        when(sysUserService.verifyCredentials(any(), anyString(), anyString(), eq(request), any()))
                .thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("Admin1234");
        AdminLoginVO vo = service.login(dto, request, response);
        assertTrue(Boolean.TRUE.equals(vo.getRequiresTotp()));
        assertNotNull(vo.getChallengeToken());
        verify(valueOps).set(startsWith("linkx:admin:totp:challenge:"), contains("3|verify"), any());

        linkxProperties.getAuth().setAdminTotpRequired(true);
        user.setTotpEnabled(0);
        vo = service.login(dto, request, response);
        assertTrue(Boolean.TRUE.equals(vo.getRequiresTotpSetup()));
    }

    @Test
    @DisplayName("TOTP 验证登录")
    void verifyTotpLogin() throws Exception {
        String secret = TotpUtils.generateSecret();
        SysUser user = adminUser(4L);
        user.setTotpEnabled(1);
        user.setTotpSecret(secret);
        stubAdminRole(4L);
        stubRequest();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("linkx:admin:totp:challenge:tok123")).thenReturn("4|verify");
        when(sysUserMapper.selectOneById(4L)).thenReturn(user);
        when(loginAuditService.recentSuccessfulIps(4L, 10)).thenReturn(List.of());
        when(sysUserService.establishSession(eq(user), anyString(), anyString(), eq(request)))
                .thenReturn(tokenFor(4L));

        AdminTotpLoginDTO dto = new AdminTotpLoginDTO();
        dto.setChallengeToken("tok123");
        dto.setCode(totpCodeNow(secret));
        AdminLoginVO vo = service.verifyTotpLogin(dto, request, response);
        assertEquals("access-4", vo.getAccessToken());
        verify(redisTemplate).delete("linkx:admin:totp:challenge:tok123");

        dto.setCode("000000");
        when(valueOps.get("linkx:admin:totp:challenge:tok123")).thenReturn("4|verify");
        assertThrows(CustomException.class, () -> service.verifyTotpLogin(dto, request, response));
    }

    @Test
    @DisplayName("TOTP 绑定流程")
    void totpSetupAndConfirm() throws Exception {
        SysUser user = adminUser(5L);
        stubAdminRole(5L);
        when(sysUserMapper.selectOneById(5L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        var setup = service.beginTotpSetup(5L);
        assertNotNull(setup.getSecret());
        verify(valueOps).set(eq("linkx:admin:totp:setup:5"), anyString(), any());

        user.setTotpEnabled(1);
        assertThrows(CustomException.class, () -> service.beginTotpSetup(5L));

        user.setTotpEnabled(0);
        when(valueOps.get("linkx:admin:totp:challenge:setupTok")).thenReturn("5|setup");
        AdminTotpChallengeDTO challenge = new AdminTotpChallengeDTO();
        challenge.setChallengeToken("setupTok");
        assertNotNull(service.beginTotpSetupWithChallenge(challenge));

        String pending = TotpUtils.generateSecret();
        when(valueOps.get("linkx:admin:totp:setup:5")).thenReturn(pending);
        AdminTotpConfirmDTO confirm = new AdminTotpConfirmDTO();
        confirm.setCode(totpCodeNow(pending));
        AdminLoginVO profileOnly = service.confirmTotp(5L, confirm, request, response);
        assertNotNull(profileOnly.getUser());
        verify(sysUserMapper).update(any(SysUser.class));
    }

    @Test
    @DisplayName("TOTP 关闭校验")
    void disableTotp_guards() throws Exception {
        String secret = TotpUtils.generateSecret();
        SysUser user = adminUser(6L);
        user.setTotpEnabled(1);
        user.setTotpSecret(secret);
        stubAdminRole(6L);
        when(sysUserMapper.selectOneById(6L)).thenReturn(user);

        AdminTotpDisableDTO dto = new AdminTotpDisableDTO();
        dto.setPassword("wrong");
        dto.setCode("000000");
        assertThrows(CustomException.class, () -> service.disableTotp(6L, dto));

        dto.setPassword("Admin1234");
        assertThrows(CustomException.class, () -> service.disableTotp(6L, dto));

        user.setTotpEnabled(0);
        assertThrows(CustomException.class, () -> service.disableTotp(6L, dto));

        user.setTotpEnabled(1);
        linkxProperties.getAuth().setAdminTotpRequired(true);
        assertThrows(CustomException.class, () -> service.disableTotp(6L, dto));
    }

    @Test
    @DisplayName("个人资料/菜单/权限")
    void profile_menus_permissions() {
        SysUser user = adminUser(7L);
        user.setAvatar("avatars/a.png");
        stubAdminRole(7L);
        when(sysUserMapper.selectOneById(7L)).thenReturn(user);
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        AdminUserProfileVO me = service.me(7L);
        assertEquals("admin", me.getUsername());
        assertTrue(me.getAvatar().startsWith("/media/avatars/7"));

        AdminProfileUpdateDTO update = new AdminProfileUpdateDTO();
        update.setNickname(" NewNick ");
        update.setEmail("NEW@test.com");
        update.setAvatar("");
        when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        AdminUserProfileVO updated = service.updateProfile(7L, update);
        assertNotNull(updated);
        verify(sysUserMapper).update(any(SysUser.class));

        update.setNickname("   ");
        assertThrows(CustomException.class, () -> service.updateProfile(7L, update));

        when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        update.setNickname("ok");
        update.setEmail("taken@test.com");
        assertThrows(CustomException.class, () -> service.updateProfile(7L, update));

        when(adminMenuService.treeForUser(7L)).thenReturn(List.of(
                AdminMenuTreeVO.builder().id(1L).name("dashboard").title("Dashboard").build()));
        assertEquals(1, service.menus(7L).size());
        assertTrue(service.permissions(7L).contains("admin:read"));
    }

    @Test
    @DisplayName("登出与刷新 Token")
    void logout_refresh() {
        stubRequest();
        AdminLogoutDTO logoutDto = new AdminLogoutDTO();
        logoutDto.setRefreshToken("r1");
        service.logout(logoutDto, "Bearer acc1", request, response);
        verify(tokenService).logout("acc1", "r1");
        verify(tokenCookieUtil).clearTokenCookies(eq(response), eq(false));

        when(tokenCookieUtil.readAccessToken(request)).thenReturn("cookieAcc");
        when(tokenCookieUtil.readRefreshToken(request)).thenReturn("cookieRef");
        service.logout(null, "  ", request, response);
        verify(tokenService).logout("cookieAcc", "cookieRef");

        AdminRefreshDTO refreshDto = new AdminRefreshDTO();
        refreshDto.setRefreshToken("refTok");
        TokenVO refreshed = tokenFor(8L);
        stubAdminRole(8L);
        when(tokenService.refreshAccessToken(eq("refTok"), nullable(String.class))).thenReturn(refreshed);
        when(sysUserMapper.selectOneById(8L)).thenReturn(adminUser(8L));
        AdminLoginVO login = service.refresh(refreshDto, request, response);
        assertEquals("access-8", login.getAccessToken());

        when(tokenService.refreshAccessToken(eq("refTok"), nullable(String.class)))
                .thenThrow(new CustomException(401, "expired"));
        assertThrows(CustomException.class, () -> service.refresh(refreshDto, request, response));
        verify(tokenCookieUtil, atLeastOnce()).clearTokenCookies(eq(response), eq(false));

        AdminRefreshDTO empty = new AdminRefreshDTO();
        when(tokenCookieUtil.readRefreshToken(request)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.refresh(empty, request, response));
    }

    @Test
    @DisplayName("挑战令牌校验")
    void challengeGuards() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        AdminTotpConfirmDTO dto = new AdminTotpConfirmDTO();
        dto.setChallengeToken("bad");
        assertThrows(CustomException.class, () -> service.confirmTotp(null, dto, request, response));

        when(valueOps.get("linkx:admin:totp:challenge:expired")).thenReturn(null);
        dto.setChallengeToken("expired");
        assertThrows(CustomException.class, () -> service.confirmTotp(1L, dto, request, response));

        when(valueOps.get("linkx:admin:totp:challenge:wrongMode")).thenReturn("1|verify");
        dto.setChallengeToken("wrongMode");
        assertThrows(CustomException.class, () -> service.beginTotpSetupWithChallenge(
                new AdminTotpChallengeDTO() {{ setChallengeToken("wrongMode"); }}));
    }
}
