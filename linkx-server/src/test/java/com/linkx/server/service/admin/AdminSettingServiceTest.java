package com.linkx.server.service.admin;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.MailSenderHolder;
import com.linkx.server.controller.admin.dto.AdminSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.ClientSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.LoginSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailTemplateSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.PasswordSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.RegisterSettingUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.entity.SysRuntimeSetting;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysRuntimeSettingMapper;
import com.linkx.server.service.EmailService;
import com.linkx.server.service.admin.impl.AdminSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminSettingService 系统配置")
class AdminSettingServiceTest {

    private static final Long OPERATOR_ID = 1L;

    @Mock SysRuntimeSettingMapper runtimeSettingMapper;
    @Mock EmailService emailService;
    @Mock Environment environment;

    private LinkxProperties linkxProperties;
    private MailSenderHolder mailSenderHolder;
    private AdminSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getApp().setVersion("1.0.0");
        linkxProperties.getApp().setChannel("stable");
        linkxProperties.getAuth().setAdminCaptchaEnabled(true);
        linkxProperties.getAuth().setAdminTotpRequired(true);
        linkxProperties.getAuth().setCaptchaEnabled(true);
        mailSenderHolder = new MailSenderHolder(linkxProperties);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        service = new AdminSettingServiceImpl(
                linkxProperties, runtimeSettingMapper, emailService, mailSenderHolder, environment);
    }

    private SysRuntimeSetting existingRow() {
        return SysRuntimeSetting.builder()
                .id(SysRuntimeSetting.SINGLETON_ID)
                .adminCaptchaEnabled(true)
                .adminTotpRequired(true)
                .clientCaptchaEnabled(true)
                .clientRegisterEnabled(true)
                .clientForgotPasswordEmailEnabled(true)
                .passwordMinLength(8)
                .passwordMaxLength(32)
                .appVersion("1.0.0")
                .appChannel("stable")
                .maxUploadBytes(10_485_760L)
                .mailHost("smtp.test.com")
                .mailPort(587)
                .mailFrom("noreply@test.com")
                .mailStartTls(true)
                .mailSsl(false)
                .mailCodeExpireMinutes(10)
                .build();
    }

    @Nested
    @DisplayName("读取配置")
    class GetSettings {
        @Test
        @DisplayName("getSettings 返回各分组")
        void getSettings_ok() {
            AdminSettingVO vo = service.getSettings();
            assertNotNull(vo.getRegister());
            assertNotNull(vo.getLogin());
            assertNotNull(vo.getPassword());
            assertNotNull(vo.getAdmin());
            assertNotNull(vo.getClient());
            assertNotNull(vo.getMail());
            assertNotNull(vo.getMailTemplates());
            assertEquals("1.0.0", vo.getClient().getAppVersion());
            assertTrue(vo.getAdmin().getCaptchaEnabled());
        }
    }

    @Nested
    @DisplayName("更新配置")
    class UpdateSettings {
        @Test
        @DisplayName("updateAdminSide 管理端验证码")
        void updateAdminSide() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            AdminSideSettingUpdateDTO dto = new AdminSideSettingUpdateDTO();
            dto.setCaptchaEnabled(false);
            AdminSettingVO vo = service.updateAdminSide(dto, OPERATOR_ID);
            assertFalse(vo.getAdmin().getCaptchaEnabled());
            verify(runtimeSettingMapper).update(any(SysRuntimeSetting.class));
        }

        @Test
        @DisplayName("updateClientSide 客户端版本")
        void updateClientSide() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            ClientSideSettingUpdateDTO dto = new ClientSideSettingUpdateDTO();
            dto.setCaptchaEnabled(true);
            dto.setAppVersion("2.0.0");
            dto.setAppChannel("beta");
            dto.setReleaseNotes("notes");
            dto.setDownloadUrl("https://dl.test/app");
            dto.setForceUpdate(false);
            dto.setMinSupportedVersion("1.5.0");
            dto.setMaxUploadBytes(20_971_520L);
            dto.setSensitiveFilterEnabled(true);
            dto.setSupportEmail("support@test.com");
            dto.setSupportPhone("400-000");
            dto.setFeedbackSlaHours(48);
            AdminSettingVO vo = service.updateClientSide(dto, OPERATOR_ID);
            assertEquals("2.0.0", vo.getClient().getAppVersion());
            assertEquals("beta", vo.getClient().getAppChannel());
        }

        @Test
        @DisplayName("updateClientSide 最低版本高于应用版本拒绝")
        void updateClientSide_versionRejected() {
            ClientSideSettingUpdateDTO dto = new ClientSideSettingUpdateDTO();
            dto.setCaptchaEnabled(true);
            dto.setAppVersion("1.0.0");
            dto.setAppChannel("stable");
            dto.setForceUpdate(false);
            dto.setMinSupportedVersion("2.0.0");
            dto.setMaxUploadBytes(10_485_760L);
            dto.setSensitiveFilterEnabled(true);
            dto.setFeedbackSlaHours(24);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.updateClientSide(dto, OPERATOR_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("updateRegister 注册开关")
        void updateRegister() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            RegisterSettingUpdateDTO dto = new RegisterSettingUpdateDTO();
            dto.setRegisterEnabled(false);
            dto.setForgotPasswordEmailEnabled(true);
            AdminSettingVO vo = service.updateRegister(dto, OPERATOR_ID);
            assertFalse(vo.getRegister().getRegisterEnabled());
            assertTrue(vo.getRegister().getForgotPasswordEmailEnabled());
        }

        @Test
        @DisplayName("updateLogin 登录策略")
        void updateLogin() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            LoginSettingUpdateDTO dto = new LoginSettingUpdateDTO();
            LoginSettingUpdateDTO.Side client = new LoginSettingUpdateDTO.Side();
            client.setCaptchaEnabled(true);
            client.setMaxAttempts(5);
            client.setLockDurationMinutes(30);
            LoginSettingUpdateDTO.Side admin = new LoginSettingUpdateDTO.Side();
            admin.setCaptchaEnabled(true);
            admin.setMaxAttempts(3);
            admin.setLockDurationMinutes(60);
            admin.setTotpRequired(true);
            dto.setClient(client);
            dto.setAdmin(admin);
            AdminSettingVO vo = service.updateLogin(dto, OPERATOR_ID);
            assertEquals(5, vo.getLogin().getClient().getMaxAttempts());
            assertTrue(vo.getLogin().getAdmin().getTotpRequired());
        }

        @Test
        @DisplayName("updateLogin 生产环境禁止关闭 TOTP")
        void updateLogin_prodGuard() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
            LoginSettingUpdateDTO dto = new LoginSettingUpdateDTO();
            LoginSettingUpdateDTO.Side client = new LoginSettingUpdateDTO.Side();
            client.setCaptchaEnabled(true);
            client.setMaxAttempts(5);
            client.setLockDurationMinutes(30);
            LoginSettingUpdateDTO.Side admin = new LoginSettingUpdateDTO.Side();
            admin.setCaptchaEnabled(true);
            admin.setMaxAttempts(3);
            admin.setLockDurationMinutes(60);
            admin.setTotpRequired(false);
            dto.setClient(client);
            dto.setAdmin(admin);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.updateLogin(dto, OPERATOR_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("updatePassword 密码策略")
        void updatePassword() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            PasswordSettingUpdateDTO dto = new PasswordSettingUpdateDTO();
            dto.setMinLength(10);
            dto.setMaxLength(64);
            dto.setRequireUpperLower(true);
            dto.setRequireDigit(true);
            dto.setRequireSpecial(false);
            AdminSettingVO vo = service.updatePassword(dto, OPERATOR_ID);
            assertEquals(10, vo.getPassword().getMinLength());
            assertEquals(64, vo.getPassword().getMaxLength());
        }

        @Test
        @DisplayName("updatePassword 最小长度大于最大长度拒绝")
        void updatePassword_lengthRejected() {
            PasswordSettingUpdateDTO dto = new PasswordSettingUpdateDTO();
            dto.setMinLength(20);
            dto.setMaxLength(10);
            dto.setRequireUpperLower(true);
            dto.setRequireDigit(true);
            dto.setRequireSpecial(false);
            assertThrows(CustomException.class, () -> service.updatePassword(dto, OPERATOR_ID));
        }

        @Test
        @DisplayName("updateMail SMTP 配置")
        void updateMail() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            MailSettingUpdateDTO dto = new MailSettingUpdateDTO();
            dto.setHost(" smtp.new.com ");
            dto.setPort(465);
            dto.setUsername("user");
            dto.setPassword("secret");
            dto.setFrom("noreply@new.com");
            dto.setFromName("LinkX");
            dto.setStartTls(false);
            dto.setSsl(true);
            dto.setCodeExpireMinutes(15);
            AdminSettingVO vo = service.updateMail(dto, OPERATOR_ID);
            assertEquals("smtp.new.com", vo.getMail().getHost());
            assertTrue(vo.getMail().getSsl());
        }

        @Test
        @DisplayName("updateMail STARTTLS 与 SSL 同时开启拒绝")
        void updateMail_tlsSslRejected() {
            MailSettingUpdateDTO dto = new MailSettingUpdateDTO();
            dto.setHost("smtp.test.com");
            dto.setPort(587);
            dto.setFrom("a@test.com");
            dto.setStartTls(true);
            dto.setSsl(true);
            dto.setCodeExpireMinutes(10);
            assertThrows(CustomException.class, () -> service.updateMail(dto, OPERATOR_ID));
        }

        @Test
        @DisplayName("updateMailTemplates 模板内容")
        void updateMailTemplates() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            MailTemplateSettingUpdateDTO dto = new MailTemplateSettingUpdateDTO();
            MailTemplateSettingUpdateDTO.Template reg = new MailTemplateSettingUpdateDTO.Template();
            reg.setSubject("Reg Sub");
            reg.setHtml("<p>Reg</p>");
            MailTemplateSettingUpdateDTO.Template reset = new MailTemplateSettingUpdateDTO.Template();
            reset.setSubject("Reset Sub");
            reset.setHtml("<p>Reset</p>");
            MailTemplateSettingUpdateDTO.Template welcome = new MailTemplateSettingUpdateDTO.Template();
            welcome.setSubject("Welcome Sub");
            welcome.setHtml("<p>Welcome</p>");
            dto.setRegister(reg);
            dto.setReset(reset);
            dto.setWelcome(welcome);
            AdminSettingVO vo = service.updateMailTemplates(dto, OPERATOR_ID);
            assertEquals("Reg Sub", vo.getMailTemplates().getRegister().getSubject());
            assertFalse(vo.getMailTemplates().getRegister().getUsingDefault());
        }

        @Test
        @DisplayName("updateSettings 单分组与空请求")
        void updateSettings_partialAndEmpty() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existingRow());
            RegisterSettingUpdateDTO register = new RegisterSettingUpdateDTO();
            register.setRegisterEnabled(false);
            register.setForgotPasswordEmailEnabled(true);
            AdminSettingUpdateDTO dto = new AdminSettingUpdateDTO();
            dto.setRegister(register);
            AdminSettingVO vo = service.updateSettings(dto, OPERATOR_ID);
            assertFalse(vo.getRegister().getRegisterEnabled());

            AdminSettingUpdateDTO empty = new AdminSettingUpdateDTO();
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.updateSettings(empty, OPERATOR_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("首次写入 insert 新行")
        void firstPersist_inserts() {
            when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(null);
            AdminSideSettingUpdateDTO dto = new AdminSideSettingUpdateDTO();
            dto.setCaptchaEnabled(false);
            service.updateAdminSide(dto, OPERATOR_ID);
            verify(runtimeSettingMapper).insert(any(SysRuntimeSetting.class));
        }
    }

    @Nested
    @DisplayName("邮件测试")
    class MailTest {
        @Test
        @DisplayName("testForgotPasswordEmail 成功")
        void testForgotPasswordEmail_ok() {
            String msg = service.testForgotPasswordEmail("User@Test.COM");
            assertTrue(msg.contains("测试邮件已发送"));
            verify(emailService).sendPasswordResetCode("user@test.com", "admin-test", "000000");
        }

        @Test
        @DisplayName("testForgotPasswordEmail 空邮箱拒绝")
        void testForgotPasswordEmail_empty() {
            assertThrows(CustomException.class, () -> service.testForgotPasswordEmail("  "));
        }
    }
}
