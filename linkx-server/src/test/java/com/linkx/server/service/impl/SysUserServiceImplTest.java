package com.linkx.server.service.impl;

import com.linkx.server.common.LoginSide;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.metrics.LinkxMetrics;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.UserPreference;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.*;
import com.linkx.server.service.admin.AdminRiskEventService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SysUserServiceImpl 用户服务")
class SysUserServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final String USERNAME = "alice";
    private static final String PASSWORD = "Test1234abcd";

    @Mock SysUserMapper sysUserMapper;
    @Mock TokenService tokenService;
    @Mock LoginAuditService loginAuditService;
    @Mock RateLimitService rateLimitService;
    @Mock FileStorageService fileStorageService;
    @Mock CaptchaService captchaService;
    @Mock EmailService emailService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock UserPreferenceService userPreferenceService;
    @Mock DeviceSessionService deviceSessionService;
    @Mock DeviceSecurityService deviceSecurityService;
    @Mock RbacService rbacService;
    @Mock ComplianceService complianceService;
    @Mock LinkxMetrics linkxMetrics;
    @Mock PasswordPolicyService passwordPolicyService;
    @Mock AdminRiskEventService adminRiskEventService;
    @Mock HttpServletRequest request;

    private LinkxProperties linkxProperties;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getAuth().setRegisterEnabled(true);
        linkxProperties.getAuth().setForgotPasswordEmailEnabled(true);
        linkxProperties.getMail().setCodeExpireMinutes(10);

        SysUserServiceImpl raw = new SysUserServiceImpl(
                tokenService, loginAuditService, rateLimitService, fileStorageService,
                captchaService, emailService, linkxProperties, redisTemplate,
                userPreferenceService, deviceSessionService, deviceSecurityService,
                rbacService, complianceService, linkxMetrics, passwordPolicyService,
                adminRiskEventService);
        ReflectionTestUtils.setField(raw, "mapper", sysUserMapper);
        service = spy(raw);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(rateLimitService.isAccountLocked(anyString(), any())).thenReturn(false);
        when(rateLimitService.checkLoginRateLimit(anyString(), any(), any())).thenReturn(false);
        doReturn(true).when(service).updateById(any(SysUser.class));
        stubRequest();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private void stubRequest() {
        when(request.getHeader("X-Device-Id")).thenReturn(null);
        when(request.getHeader("X-Device-Name")).thenReturn(null);
        when(request.getHeader("X-Device-Type")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private SysUser activeUser() {
        return SysUser.builder()
                .id(USER_ID)
                .username(USERNAME)
                .password(PasswordEncoderHolder.encode(PASSWORD))
                .nickname("Alice")
                .email("alice@test.com")
                .avatar("/default-avatar.svg")
                .status(1)
                .build();
    }

    private LoginDTO loginDto() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(USERNAME);
        dto.setPassword(PASSWORD);
        return dto;
    }

    @Nested
    @DisplayName("verifyCredentials")
    class VerifyCredentials {
        @Test
        @DisplayName("账号 Redis 锁定")
        void accountLocked() {
            when(rateLimitService.isAccountLocked(USERNAME, LoginSide.CLIENT)).thenReturn(true);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.verifyCredentials(loginDto(), "1.1.1.1", "JUnit", request, LoginSide.CLIENT));
            assertEquals(429, ex.getCode());
            verify(linkxMetrics).recordLoginFailure();
        }

        @Test
        @DisplayName("管理端锁定提示分钟数")
        void adminAccountLocked() {
            linkxProperties.getAuth().setAdminLockDurationMinutes(15);
            when(rateLimitService.isAccountLocked(USERNAME, LoginSide.ADMIN)).thenReturn(true);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.verifyCredentials(loginDto(), "1.1.1.1", "JUnit", request, LoginSide.ADMIN));
            assertTrue(ex.getMessage().contains("15"));
        }

        @Test
        @DisplayName("用户不存在")
        void userNotFound() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.verifyCredentials(loginDto(), "1.1.1.1", "JUnit", request, null));
            assertEquals(400, ex.getCode());
            verify(loginAuditService).record(isNull(), eq(USERNAME), anyString(), anyString(), eq(false), anyString());
        }

        @Test
        @DisplayName("密码错误")
        void wrongPassword() {
            SysUser user = activeUser();
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);
            LoginDTO dto = loginDto();
            dto.setPassword("wrong");
            assertThrows(CustomException.class,
                    () -> service.verifyCredentials(dto, "1.1.1.1", "JUnit", request, LoginSide.CLIENT));
            verify(rateLimitService).checkLoginRateLimit(eq(USERNAME), eq(request), eq(LoginSide.CLIENT));
        }

        @Test
        @DisplayName("账号已停用")
        void disabledAccount() {
            SysUser user = activeUser();
            user.setStatus(0);
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.verifyCredentials(loginDto(), "1.1.1.1", "JUnit", request, LoginSide.CLIENT));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("校验成功")
        void success() {
            SysUser user = activeUser();
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);
            SysUser result = service.verifyCredentials(loginDto(), "1.1.1.1", "JUnit", request, LoginSide.CLIENT);
            assertEquals(USER_ID, result.getId());
        }

        @Test
        @DisplayName("低 cost 密码透明升级")
        void passwordRehash() {
            SysUser user = activeUser();
            user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10)
                    .encode(PASSWORD));
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);
            service.verifyCredentials(loginDto(), "1.1.1.1", "JUnit", request, LoginSide.CLIENT);
            verify(service).updateById(any(SysUser.class));
        }

    }

    @Nested
    @DisplayName("establishSession")
    class EstablishSession {
        @Test
        @DisplayName("默认设备信息")
        void defaultDevice() {
            SysUser user = activeUser();
            TokenVO token = TokenVO.builder()
                    .accessToken("acc")
                    .refreshToken("ref")
                    .user(UserInfoVO.builder().id(USER_ID).username(USERNAME).build())
                    .build();
            when(tokenService.issueTokenPair(user, "default-web-device")).thenReturn(token);

            TokenVO result = service.establishSession(user, "1.1.1.1", "JUnit", request);
            assertEquals("acc", result.getAccessToken());
            verify(deviceSecurityService).assertDeviceAllowed(USER_ID, "default-web-device");
            verify(deviceSessionService).createOrUpdate(
                    eq(USER_ID), eq("default-web-device"), eq("Web 浏览器"), eq("Web"), anyString(), anyString());
            verify(linkxMetrics).recordLoginSuccess();
        }

        @Test
        @DisplayName("自定义设备头")
        void customDeviceHeaders() {
            when(request.getHeader("X-Device-Id")).thenReturn("phone-1");
            when(request.getHeader("X-Device-Name")).thenReturn("My Phone");
            when(request.getHeader("X-Device-Type")).thenReturn("iOS");
            SysUser user = activeUser();
            when(tokenService.issueTokenPair(user, "phone-1")).thenReturn(TokenVO.builder().build());

            service.establishSession(user, "1.1.1.1", "JUnit", request);
            verify(deviceSessionService).createOrUpdate(
                    eq(USER_ID), eq("phone-1"), eq("My Phone"), eq("iOS"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("onLoginFailure")
    class OnLoginFailure {
        @Test
        @DisplayName("空用户名直接返回")
        void blankUsername() {
            service.onLoginFailure("  ", request, LoginSide.CLIENT);
            verify(rateLimitService, never()).checkLoginRateLimit(anyString(), any(), any());
        }

        @Test
        @DisplayName("已锁定再次失败")
        void alreadyLocked() {
            when(rateLimitService.isAccountLocked(USERNAME, LoginSide.CLIENT)).thenReturn(true);
            assertThrows(CustomException.class,
                    () -> service.onLoginFailure(USERNAME, request, LoginSide.CLIENT));
        }

        @Test
        @DisplayName("达阈值新锁定")
        void newlyLocked() {
            when(rateLimitService.checkLoginRateLimit(USERNAME, request, LoginSide.CLIENT)).thenReturn(true);
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activeUser());
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.onLoginFailure(USERNAME, request, LoginSide.CLIENT));
            assertEquals(429, ex.getCode());
            verify(service).updateById(any(SysUser.class));
            verify(adminRiskEventService).recordLoginLock(any(), eq(USERNAME), anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("未达阈值")
        void notLocked() {
            service.onLoginFailure(USERNAME, request, null);
            verify(rateLimitService).checkLoginRateLimit(USERNAME, request, LoginSide.CLIENT);
        }

        @Test
        @DisplayName("达阈值但用户不存在")
        void newlyLockedUnknownUser() {
            when(rateLimitService.checkLoginRateLimit(USERNAME, request, LoginSide.ADMIN)).thenReturn(true);
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class,
                    () -> service.onLoginFailure(USERNAME, request, LoginSide.ADMIN));
            verify(service, never()).updateById(any());
        }

        @Test
        @DisplayName("风险事件写入失败不阻断")
        void riskEventFailureIgnored() {
            when(rateLimitService.checkLoginRateLimit(USERNAME, request, LoginSide.CLIENT)).thenReturn(true);
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activeUser());
            doThrow(new RuntimeException("risk db")).when(adminRiskEventService)
                    .recordLoginLock(any(), anyString(), anyString(), anyString(), anyInt());
            assertThrows(CustomException.class,
                    () -> service.onLoginFailure(USERNAME, request, LoginSide.CLIENT));
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {
        @Test
        @DisplayName("用户不存在")
        void notFound() {
            doReturn(null).when(service).getById(99L);
            assertThrows(CustomException.class, () -> service.updateProfile(99L, new UpdateProfileDTO()));
        }

        @Test
        @DisplayName("更新各字段")
        void updateFields() {
            SysUser user = activeUser();
            doReturn(user).when(service).getById(USER_ID);
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNickname("New Nick");
            dto.setSignature("hello");
            dto.setGender("女");
            dto.setBirthday(1_700_000_000_000L);
            dto.setCountry("中国");
            dto.setProvince("广东");
            dto.setRegion("深圳");

            SysUser updated = service.updateProfile(USER_ID, dto);
            assertEquals("New Nick", updated.getNickname());
            assertEquals("hello", updated.getSignature());
            verify(service).updateById(user);
        }

        @Test
        @DisplayName("空字符串清空可选字段")
        void clearOptionalFields() {
            SysUser user = activeUser();
            user.setGender("男");
            user.setCountry("中国");
            doReturn(user).when(service).getById(USER_ID);
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setGender("");
            dto.setCountry("");
            dto.setSignature("");

            service.updateProfile(USER_ID, dto);
            assertNull(user.getGender());
            assertNull(user.getCountry());
            assertEquals("", user.getSignature());
        }

        @Test
        @DisplayName("无变更不写入")
        void noChanges() {
            SysUser user = activeUser();
            doReturn(user).when(service).getById(USER_ID);
            service.updateProfile(USER_ID, new UpdateProfileDTO());
            verify(service, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("头像与背景")
    class AvatarAndBackground {
        @Test
        @DisplayName("updateAvatar 用户不存在")
        void avatarNotFound() {
            doReturn(null).when(service).getById(1L);
            assertThrows(CustomException.class, () -> service.updateAvatar(1L, "avatars/x.png"));
        }

        @Test
        @DisplayName("updateAvatar 删除旧 MinIO key")
        void avatarReplace() {
            SysUser user = activeUser();
            user.setAvatar("avatars/old.png");
            doReturn(user).when(service).getById(USER_ID);
            service.updateAvatar(USER_ID, "avatars/new.png");
            verify(fileStorageService).deleteFile("avatars/old.png");
            assertEquals("avatars/new.png", user.getAvatar());
        }

        @Test
        @DisplayName("updateAvatar 外链不删")
        void avatarExternalSkipDelete() {
            SysUser user = activeUser();
            user.setAvatar("https://cdn.example/a.png");
            doReturn(user).when(service).getById(USER_ID);
            service.updateAvatar(USER_ID, "avatars/new.png");
            verify(fileStorageService, never()).deleteFile(anyString());
        }

        @Test
        @DisplayName("updateAvatar 删除失败仍更新")
        void avatarDeleteFailure() {
            SysUser user = activeUser();
            user.setAvatar("avatars/old.png");
            doReturn(user).when(service).getById(USER_ID);
            doThrow(new RuntimeException("minio down")).when(fileStorageService).deleteFile("avatars/old.png");
            service.updateAvatar(USER_ID, "avatars/new.png");
            assertEquals("avatars/new.png", user.getAvatar());
        }

        @Test
        @DisplayName("updateMomentsBackground")
        void momentsBackground() {
            UserPreference pref = UserPreference.builder().userId(USER_ID).momentsBackground("bg/old.png").build();
            when(userPreferenceService.getOrDefault(USER_ID)).thenReturn(pref);
            service.updateMomentsBackground(USER_ID, "bg/new.png");
            verify(fileStorageService).deleteFile("bg/old.png");
            verify(userPreferenceService).upsert(eq(USER_ID), any(UserPreference.class));
        }
    }

    @Nested
    @DisplayName("密码相关")
    class PasswordOps {
        @BeforeEach
        void initTx() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @Test
        @DisplayName("changePassword 用户不存在")
        void changeUserMissing() {
            doReturn(null).when(service).getById(USER_ID);
            assertThrows(CustomException.class,
                    () -> service.changePassword(USER_ID, PASSWORD, "NewPass1234"));
        }

        @Test
        @DisplayName("changePassword 旧密码错误")
        void changeWrongOld() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            assertThrows(CustomException.class,
                    () -> service.changePassword(USER_ID, "bad", "NewPass1234"));
        }

        @Test
        @DisplayName("changePassword 成功")
        void changeSuccess() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            service.changePassword(USER_ID, PASSWORD, "NewPass1234");
            verify(passwordPolicyService).validate("NewPass1234");
            verify(service).updateById(any(SysUser.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(tokenService).revokeAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("resetPassword 验证码缺失")
        void resetCaptchaMissing() {
            when(captchaService.isEnabled()).thenReturn(true);
            assertThrows(CustomException.class,
                    () -> service.resetPassword(USER_ID, "", "", "NewPass1234"));
        }

        @Test
        @DisplayName("resetPassword 验证码通过")
        void resetWithCaptcha() {
            when(captchaService.isEnabled()).thenReturn(true);
            doReturn(activeUser()).when(service).getById(USER_ID);
            service.resetPassword(USER_ID, "cid", "1234", "NewPass1234");
            verify(captchaService).validateForOwner(String.valueOf(USER_ID), "cid", "1234");
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(tokenService).revokeAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("resetPassword 用户不存在")
        void resetUserMissing() {
            when(captchaService.isEnabled()).thenReturn(false);
            doReturn(null).when(service).getById(USER_ID);
            assertThrows(CustomException.class,
                    () -> service.resetPassword(USER_ID, null, null, "NewPass1234"));
        }

        @Test
        @DisplayName("verifyEmailResetCode 空验证码")
        void verifyEmptyCode() {
            assertThrows(CustomException.class,
                    () -> service.verifyEmailResetCode(USERNAME, "  ", "1.1.1.1"));
        }

        @Test
        @DisplayName("verifyEmailResetCode 无 Redis 记录")
        void verifyNoRedis() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn(null);
            assertThrows(CustomException.class,
                    () -> service.verifyEmailResetCode(USERNAME, "123456", "1.1.1.1"));
        }

        @Test
        @DisplayName("verifyEmailResetCode 错误码累加")
        void verifyWrongCode() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn("482915");
            when(valueOps.increment("linkx:reset-email:attempts:" + USERNAME)).thenReturn(1L);
            when(redisTemplate.getExpire("linkx:reset-email:attempts:" + USERNAME)).thenReturn(-1L);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.verifyEmailResetCode(USERNAME, "000000", "1.1.1.1"));
            assertTrue(ex.getMessage().contains("还可再尝试"));
        }

        @Test
        @DisplayName("verifyEmailResetCode 超过次数")
        void verifyTooManyAttempts() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn("482915");
            when(valueOps.increment("linkx:reset-email:attempts:" + USERNAME)).thenReturn(5L);
            assertThrows(CustomException.class,
                    () -> service.verifyEmailResetCode(USERNAME, "000000", "1.1.1.1"));
            verify(redisTemplate).delete("linkx:reset-email:" + USERNAME);
        }

        @Test
        @DisplayName("verifyEmailResetCode 成功")
        void verifySuccess() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn("482915");
            assertDoesNotThrow(() -> service.verifyEmailResetCode(USERNAME, "482915", "1.1.1.1"));
            verify(redisTemplate).delete("linkx:reset-email:attempts:" + USERNAME);
        }

        @Test
        @DisplayName("resetPasswordByEmail 用户不存在")
        void resetByEmailUserMissing() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn("482915");
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class,
                    () -> service.resetPasswordByEmail(USERNAME, "482915", "NewPass1234", "1.1.1.1"));
        }

        @Test
        @DisplayName("resetPasswordByEmail 全流程")
        void resetByEmailFlow() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn("482915");
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activeUser());
            service.resetPasswordByEmail(USERNAME, "482915", "NewPass1234", "1.1.1.1");
            verify(passwordPolicyService).validate("NewPass1234");
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(tokenService).revokeAllUserTokens(USER_ID);
            verify(emailService).sendPasswordChangedNotification(anyString(), eq(USERNAME), anyString());
        }
    }

    @Nested
    @DisplayName("注册与邮箱")
    class RegisterAndEmail {
        @Test
        @DisplayName("findEmailByUsername")
        void findEmail() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activeUser());
            assertEquals("alice@test.com", service.findEmailByUsername(USERNAME));
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertNull(service.findEmailByUsername("missing"));
        }

        @Test
        @DisplayName("sendPasswordResetEmailCode 未启用")
        void resetEmailDisabled() {
            linkxProperties.getAuth().setForgotPasswordEmailEnabled(false);
            assertThrows(CustomException.class,
                    () -> service.sendPasswordResetEmailCode(USERNAME, "1.1.1.1"));
        }

        @Test
        @DisplayName("sendPasswordResetEmailCode 用户不存在静默成功")
        void resetEmailUserMissing() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertDoesNotThrow(() -> service.sendPasswordResetEmailCode(USERNAME, "1.1.1.1"));
            verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("sendPasswordResetEmailCode 成功发信")
        void resetEmailSent() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activeUser());
            service.sendPasswordResetEmailCode(USERNAME, "1.1.1.1");
            verify(valueOps).set(eq("linkx:reset-email:" + USERNAME), anyString(), any());
            verify(emailService).sendPasswordResetCode(eq("alice@test.com"), eq(USERNAME), anyString());
        }

        @Test
        @DisplayName("sendRegisterEmailCode 注册关闭")
        void registerCodeDisabled() {
            linkxProperties.getAuth().setRegisterEnabled(false);
            assertThrows(CustomException.class,
                    () -> service.sendRegisterEmailCode("a@test.com", "bob", "1.1.1.1"));
        }

        @Test
        @DisplayName("sendRegisterEmailCode 邮箱为空")
        void registerCodeEmptyEmail() {
            assertThrows(CustomException.class,
                    () -> service.sendRegisterEmailCode("  ", "bob", "1.1.1.1"));
        }

        @Test
        @DisplayName("sendRegisterEmailCode 邮箱已存在")
        void registerCodeExistingEmail() {
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            service.sendRegisterEmailCode("exists@test.com", "bob", "1.1.1.1");
            verify(emailService, never()).sendRegisterCode(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("sendRegisterEmailCode 成功")
        void registerCodeSuccess() {
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            service.sendRegisterEmailCode("new@test.com", "bob", "1.1.1.1");
            verify(emailService).sendRegisterCode(eq("new@test.com"), eq("bob"), anyString());
        }

        @Test
        @DisplayName("sendRegisterEmailCode 发信失败回滚 Redis")
        void registerCodeMailFailure() {
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            doThrow(new CustomException(500, "smtp down")).when(emailService)
                    .sendRegisterCode(anyString(), anyString(), anyString());
            assertThrows(CustomException.class,
                    () -> service.sendRegisterEmailCode("new@test.com", null, "1.1.1.1"));
            verify(redisTemplate).delete("linkx:register-email:new@test.com");
        }

        @Test
        @DisplayName("sendBindEmailCode 用户不存在")
        void bindEmailCodeUserMissing() {
            doReturn(null).when(service).getById(99L);
            assertThrows(CustomException.class,
                    () -> service.sendBindEmailCode(99L, "x@test.com", "1.1.1.1"));
        }

        @Test
        @DisplayName("bindEmail 验证码过期")
        void bindEmailExpired() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            when(valueOps.get("linkx:bind-email:" + USER_ID)).thenReturn(null);
            assertThrows(CustomException.class,
                    () -> service.bindEmail(USER_ID, "new@test.com", "111111", "1.1.1.1"));
        }

        @Test
        @DisplayName("bindPhone 手机号被占用")
        void bindPhoneTaken() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            assertThrows(CustomException.class,
                    () -> service.bindPhone(USER_ID, "13800138000", PASSWORD));
        }

        @Test
        @DisplayName("register 未开放")
        void registerDisabled() {
            linkxProperties.getAuth().setRegisterEnabled(false);
            assertThrows(CustomException.class, () -> service.register(new RegisterDTO(), request));
        }

        @Test
        @DisplayName("register 邮箱验证码缺失")
        void registerMissingEmailCode() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setEmail("bob@test.com");
            dto.setEmailCode("");
            assertThrows(CustomException.class, () -> service.register(dto, request));
        }

        @Test
        @DisplayName("register 邮箱已存在")
        void registerDuplicateEmail() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setEmail("exists@test.com");
            dto.setEmailCode("123456");
            when(valueOps.get("linkx:register-email:exists@test.com")).thenReturn("123456");
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 1L);
            assertThrows(CustomException.class, () -> service.register(dto, request));
        }

        @Test
        @DisplayName("register 用户名已存在")
        void registerDuplicateUsername() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setEmail("bob@test.com");
            dto.setEmailCode("123456");
            when(valueOps.get("linkx:register-email:bob@test.com")).thenReturn("123456");
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            assertThrows(CustomException.class, () -> service.register(dto, request));
        }

        @Test
        @DisplayName("register 成功")
        void registerSuccess() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setNickname("Bob");
            dto.setEmail("bob@test.com");
            dto.setEmailCode("123456");
            when(valueOps.get("linkx:register-email:bob@test.com")).thenReturn("123456");
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            doAnswer(inv -> {
                SysUser u = inv.getArgument(0);
                u.setId(20L);
                return true;
            }).when(service).save(any(SysUser.class));

            service.register(dto, request);
            verify(rbacService).grantRole(eq(20L), anyString(), isNull());
            verify(redisTemplate).delete("linkx:register-email:bob@test.com");
            verify(emailService).sendWelcomeEmail(eq("bob@test.com"), eq("bob"), eq("Bob"));
        }

        @Test
        @DisplayName("sendBindEmailCode 邮箱被占用")
        void bindEmailCodeTaken() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            assertThrows(CustomException.class,
                    () -> service.sendBindEmailCode(USER_ID, "taken@test.com", "1.1.1.1"));
        }

        @Test
        @DisplayName("bindEmail 验证码错误")
        void bindEmailWrongCode() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            when(valueOps.get("linkx:bind-email:" + USER_ID)).thenReturn("new@test.com|111111");
            assertThrows(CustomException.class,
                    () -> service.bindEmail(USER_ID, "new@test.com", "000000", "1.1.1.1"));
        }

        @Test
        @DisplayName("bindEmail 成功")
        void bindEmailSuccess() {
            SysUser user = activeUser();
            doReturn(user).when(service).getById(USER_ID);
            when(valueOps.get("linkx:bind-email:" + USER_ID)).thenReturn("new@test.com|111111");
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            service.bindEmail(USER_ID, "new@test.com", "111111", "1.1.1.1");
            assertEquals("new@test.com", user.getEmail());
            verify(redisTemplate).delete("linkx:bind-email:" + USER_ID);
        }

        @Test
        @DisplayName("bindPhone 密码错误")
        void bindPhoneWrongPassword() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            assertThrows(CustomException.class,
                    () -> service.bindPhone(USER_ID, "13800138000", "wrong"));
        }

        @Test
        @DisplayName("bindPhone 成功")
        void bindPhoneSuccess() {
            SysUser user = activeUser();
            doReturn(user).when(service).getById(USER_ID);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            service.bindPhone(USER_ID, "13800138000", PASSWORD);
            assertEquals("13800138000", user.getPhone());
        }
    }

    @Nested
    @DisplayName("修改 LinkX ID")
    class ChangeUsername {
        @Test
        @DisplayName("changeUsername 密码错误")
        void wrongPassword() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            assertThrows(CustomException.class,
                    () -> service.changeUsername(USER_ID, "new_linkx_id", "wrong"));
        }

        @Test
        @DisplayName("changeUsername 已被占用")
        void taken() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            assertThrows(CustomException.class,
                    () -> service.changeUsername(USER_ID, "taken_id", PASSWORD));
        }

        @Test
        @DisplayName("changeUsername 成功")
        void success() {
            SysUser user = activeUser();
            doReturn(user).when(service).getById(USER_ID);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            SysUser updated = service.changeUsername(USER_ID, "new_linkx_id", PASSWORD);
            assertEquals("new_linkx_id", updated.getUsername());
        }
    }

    @Nested
    @DisplayName("账号注销与解锁")
    class AccountLifecycle {
        @Test
        @DisplayName("deleteAccount 用户不存在")
        void deleteUserMissing() {
            doReturn(null).when(service).getById(USER_ID);
            assertThrows(CustomException.class, () -> service.deleteAccount(USER_ID, PASSWORD));
        }

        @Test
        @DisplayName("deleteAccount 密码错误")
        void deleteWrongPassword() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            assertThrows(CustomException.class,
                    () -> service.deleteAccount(USER_ID, "wrong"));
        }

        @Test
        @DisplayName("deleteAccount 成功")
        void deleteSuccess() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            doReturn(true).when(service).removeById(USER_ID);
            service.deleteAccount(USER_ID, PASSWORD);
            verify(complianceService).purgeUserData(USER_ID, PASSWORD);
            verify(tokenService).revokeAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("unlockExpiredAutoLocks 无到期账号")
        void unlockEmpty() {
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            assertEquals(0, service.unlockExpiredAutoLocks());
        }

        @Test
        @DisplayName("login 委托 verify + session")
        void loginDelegates() {
            SysUser user = activeUser();
            doReturn(user).when(service).verifyCredentials(any(), anyString(), anyString(), eq(request), eq(LoginSide.CLIENT));
            TokenVO token = TokenVO.builder().accessToken("tok").build();
            doReturn(token).when(service).establishSession(user, "1.1.1.1", "JUnit", request);
            TokenVO result = service.login(loginDto(), "1.1.1.1", "JUnit", request);
            assertEquals("tok", result.getAccessToken());
        }
    }

    @Nested
    @DisplayName("extended coverage")
    class ExtendedCoverage {
        @BeforeEach
        void initTxWhenNeeded() {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.initSynchronization();
            }
        }

        @Test
        @DisplayName("register 并发唯一索引冲突")
        void registerDuplicateKey() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setEmail("bob@test.com");
            dto.setEmailCode("123456");
            when(valueOps.get("linkx:register-email:bob@test.com")).thenReturn("123456");
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                    .when(service).save(any(SysUser.class));
            assertThrows(CustomException.class, () -> service.register(dto, request));
            verify(linkxMetrics).recordRegisterFailure();
        }

        @Test
        @DisplayName("register 欢迎邮件失败不影响注册")
        void registerWelcomeMailFailure() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setNickname("Bob");
            dto.setEmail("bob@test.com");
            dto.setEmailCode("123456");
            when(valueOps.get("linkx:register-email:bob@test.com")).thenReturn("123456");
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            doAnswer(inv -> {
                SysUser u = inv.getArgument(0);
                u.setId(20L);
                return true;
            }).when(service).save(any(SysUser.class));
            doThrow(new RuntimeException("smtp")).when(emailService)
                    .sendWelcomeEmail(anyString(), anyString(), anyString());
            assertDoesNotThrow(() -> service.register(dto, request));
        }

        @Test
        @DisplayName("register 验证码错误累加")
        void registerWrongEmailCode() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword(PASSWORD);
            dto.setEmail("bob@test.com");
            dto.setEmailCode("000000");
            when(valueOps.get("linkx:register-email:bob@test.com")).thenReturn("123456");
            when(valueOps.increment("linkx:register-email:attempts:bob@test.com")).thenReturn(1L);
            when(redisTemplate.getExpire("linkx:register-email:bob@test.com")).thenReturn(600L);
            CustomException ex = assertThrows(CustomException.class, () -> service.register(dto, request));
            assertTrue(ex.getMessage().contains("还可再尝试"));
        }

        @Test
        @DisplayName("sendBindEmailCode 成功")
        void sendBindEmailCodeSuccess() {
            doReturn(activeUser()).when(service).getById(USER_ID);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            service.sendBindEmailCode(USER_ID, "bind@test.com", "1.1.1.1");
            verify(emailService).sendBindEmailCode(eq("bind@test.com"), eq(USERNAME), anyString());
        }

        @Test
        @DisplayName("sendRegisterEmailCode 通用发信失败")
        void registerCodeGenericMailFailure() {
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            doThrow(new RuntimeException("network")).when(emailService)
                    .sendRegisterCode(anyString(), anyString(), anyString());
            assertThrows(CustomException.class,
                    () -> service.sendRegisterEmailCode("new@test.com", "bob", "1.1.1.1"));
            verify(redisTemplate).delete("linkx:register-email:new@test.com");
        }

        @Test
        @DisplayName("resetPasswordByEmail afterCommit 清理失败不阻断")
        void resetByEmailAfterCommitCleanupFailure() {
            when(valueOps.get("linkx:reset-email:" + USERNAME)).thenReturn("482915");
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(activeUser());
            doThrow(new RuntimeException("redis")).when(redisTemplate).delete("linkx:reset-email:" + USERNAME);
            service.resetPasswordByEmail(USERNAME, "482915", "NewPass1234", "1.1.1.1");
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(tokenService).revokeAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("updateMomentsBackground 删除旧图失败仍更新")
        void momentsBackgroundDeleteFailure() {
            UserPreference pref = UserPreference.builder().userId(USER_ID).momentsBackground("bg/old.png").build();
            when(userPreferenceService.getOrDefault(USER_ID)).thenReturn(pref);
            doThrow(new RuntimeException("minio")).when(fileStorageService).deleteFile("bg/old.png");
            service.updateMomentsBackground(USER_ID, "bg/new.png");
            verify(userPreferenceService).upsert(eq(USER_ID), any(UserPreference.class));
        }
    }
}
