package com.linkx.server.service.admin.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.MailSenderHolder;
import com.linkx.server.config.MailTemplateDefaults;
import com.linkx.server.controller.admin.dto.AdminSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.ClientSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.LoginSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailTemplateSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.PasswordSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.RegisterSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.SecuritySettingUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.entity.SysRuntimeSetting;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysRuntimeSettingMapper;
import com.linkx.server.service.EmailService;
import com.linkx.server.service.admin.AdminSettingService;
import com.linkx.server.util.AppVersionUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettingServiceImpl implements AdminSettingService {

    private final LinkxProperties linkxProperties;
    private final SysRuntimeSettingMapper runtimeSettingMapper;
    private final EmailService emailService;
    private final MailSenderHolder mailSenderHolder;
    private final Environment environment;

    @PostConstruct
    public void loadOverridesFromDb() {
        try {
            SysRuntimeSetting row = runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID);
            if (row != null) {
                applyToProperties(row);
                log.info("Loaded runtime settings from DB (clientVersion={}, adminCaptcha={}, clientCaptcha={}, register={}, forgotEmail={})",
                        row.getAppVersion(), row.getAdminCaptchaEnabled(), row.getClientCaptchaEnabled(),
                        row.getClientRegisterEnabled(), row.getClientForgotPasswordEmailEnabled());
            }
        } catch (Exception e) {
            log.warn("Skip loading runtime settings: {}", e.getMessage());
        }
    }

    @Override
    public AdminSettingVO getSettings() {
        LinkxProperties.App app = linkxProperties.getApp();
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return AdminSettingVO.builder()
                .register(AdminSettingVO.RegisterSide.builder()
                        .registerEnabled(auth.isRegisterEnabled())
                        .forgotPasswordEmailEnabled(auth.isForgotPasswordEmailEnabled())
                        .build())
                .login(AdminSettingVO.LoginSide.builder()
                        .client(AdminSettingVO.LoginEntry.builder()
                                .captchaEnabled(auth.isCaptchaEnabled())
                                .maxAttempts(auth.getLoginMaxAttempts())
                                .lockDurationMinutes(auth.getLockDurationMinutes())
                                .build())
                        .admin(AdminSettingVO.LoginEntry.builder()
                                .captchaEnabled(auth.isAdminCaptchaEnabled())
                                .maxAttempts(auth.getAdminLoginMaxAttempts())
                                .lockDurationMinutes(auth.getAdminLockDurationMinutes())
                                .totpRequired(auth.isAdminTotpRequired())
                                .build())
                        .build())
                .password(AdminSettingVO.PasswordSide.builder()
                        .minLength(auth.getPasswordMinLength())
                        .maxLength(auth.getPasswordMaxLength())
                        .requireUpperLower(auth.isPasswordRequireUpperLower())
                        .requireDigit(auth.isPasswordRequireDigit())
                        .requireSpecial(auth.isPasswordRequireSpecial())
                        .build())
                .admin(AdminSettingVO.AdminSide.builder()
                        .captchaEnabled(auth.isAdminCaptchaEnabled())
                        .build())
                .client(AdminSettingVO.ClientSide.builder()
                        .captchaEnabled(auth.isCaptchaEnabled())
                        .appVersion(app != null ? app.getVersion() : null)
                        .appChannel(app != null ? app.getChannel() : null)
                        .releaseNotes(app != null ? app.getReleaseNotes() : null)
                        .downloadUrl(app != null ? app.getDownloadUrl() : null)
                        .forceUpdate(app != null && Boolean.TRUE.equals(app.getForceUpdate()))
                        .minSupportedVersion(app != null ? nullToEmpty(app.getMinSupportedVersion()) : "")
                        .maxUploadBytes(linkxProperties.getMinio().getMaxFileSize())
                        .sensitiveFilterEnabled(app == null || !Boolean.FALSE.equals(app.getSensitiveFilterEnabled()))
                        .supportEmail(app != null ? nullToEmpty(app.getSupportEmail()) : "")
                        .supportPhone(app != null ? nullToEmpty(app.getSupportPhone()) : "")
                        .feedbackSlaHours(resolveFeedbackSlaHours(app))
                        .feedbackEscalationEnabled(resolveFeedbackEscalationEnabled(app))
                        .feedbackEscalationAutoReassign(resolveFeedbackEscalationAutoReassign(app))
                        .feedbackEscalationIntervalHours(resolveFeedbackEscalationIntervalHours(app))
                        .reviewSlaHours(resolveReviewSlaHours(app))
                        .reviewEscalationEnabled(resolveReviewEscalationEnabled(app))
                        .reviewEscalationIntervalHours(resolveReviewEscalationIntervalHours(app))
                        .build())
                .mail(buildMailSide())
                .mailTemplates(buildMailTemplatesSide())
                .security(AdminSettingVO.SecuritySide.builder()
                        .disableFrontendDebug(linkxProperties.getSecurity().isDisableFrontendDebug())
                        .apiSignEnabled(linkxProperties.getSecurity().isApiSignEnabled())
                        .apiEncryptEnabled(linkxProperties.getSecurity().isApiEncryptEnabled())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public AdminSettingVO updateSettings(AdminSettingUpdateDTO dto, Long operatorId) {
        if (dto == null || !hasAnySection(dto)) {
            throw new CustomException(400, "至少提供一个配置分组");
        }
        if (dto.getRegister() != null) {
            updateRegister(dto.getRegister(), operatorId);
        }
        if (dto.getLogin() != null) {
            updateLogin(dto.getLogin(), operatorId);
        }
        if (dto.getPassword() != null) {
            updatePassword(dto.getPassword(), operatorId);
        }
        if (dto.getAdmin() != null) {
            updateAdminSide(dto.getAdmin(), operatorId);
        }
        if (dto.getClient() != null) {
            updateClientSide(dto.getClient(), operatorId);
        }
        if (dto.getMail() != null) {
            updateMail(dto.getMail(), operatorId);
        }
        if (dto.getMailTemplates() != null) {
            updateMailTemplates(dto.getMailTemplates(), operatorId);
        }
        if (dto.getSecurity() != null) {
            updateSecurity(dto.getSecurity(), operatorId);
        }
        return getSettings();
    }

    private static boolean hasAnySection(AdminSettingUpdateDTO dto) {
        return dto.getRegister() != null
                || dto.getLogin() != null
                || dto.getPassword() != null
                || dto.getAdmin() != null
                || dto.getClient() != null
                || dto.getMail() != null
                || dto.getMailTemplates() != null
                || dto.getSecurity() != null;
    }

    private int resolveFeedbackSlaHours(LinkxProperties.App app) {
        Integer hours = app != null ? app.getFeedbackSlaHours() : null;
        if (hours == null || hours < 1) {
            return 24;
        }
        return Math.min(hours, 720);
    }

    private boolean resolveFeedbackEscalationEnabled(LinkxProperties.App app) {
        return app != null && Boolean.TRUE.equals(app.getFeedbackEscalationEnabled());
    }

    private boolean resolveFeedbackEscalationAutoReassign(LinkxProperties.App app) {
        return app == null || !Boolean.FALSE.equals(app.getFeedbackEscalationAutoReassign());
    }

    private int resolveFeedbackEscalationIntervalHours(LinkxProperties.App app) {
        Integer hours = app != null ? app.getFeedbackEscalationIntervalHours() : null;
        if (hours == null || hours < 1) {
            return 24;
        }
        return Math.min(hours, 720);
    }

    private int resolveReviewSlaHours(LinkxProperties.App app) {
        Integer hours = app != null ? app.getReviewSlaHours() : null;
        if (hours == null || hours < 1) {
            return 24;
        }
        return Math.min(hours, 720);
    }

    private boolean resolveReviewEscalationEnabled(LinkxProperties.App app) {
        return app != null && Boolean.TRUE.equals(app.getReviewEscalationEnabled());
    }

    private int resolveReviewEscalationIntervalHours(LinkxProperties.App app) {
        Integer hours = app != null ? app.getReviewEscalationIntervalHours() : null;
        if (hours == null || hours < 1) {
            return 24;
        }
        return Math.min(hours, 720);
    }

    private AdminSettingVO.MailSide buildMailSide() {
        LinkxProperties.Mail mail = linkxProperties.getMail();
        return AdminSettingVO.MailSide.builder()
                .host(mail.getHost())
                .port(mail.getPort())
                .username(mail.getUsername())
                .passwordConfigured(StringUtils.hasText(mail.getPassword()))
                .from(mail.getFrom())
                .fromName(mail.getFromName())
                .startTls(mail.isStartTls())
                .ssl(mail.isSsl())
                .codeExpireMinutes(mail.getCodeExpireMinutes())
                .build();
    }

    private AdminSettingVO.MailTemplatesSide buildMailTemplatesSide() {
        LinkxProperties.MailTemplates t = linkxProperties.getMailTemplates();
        return AdminSettingVO.MailTemplatesSide.builder()
                .register(resolveTemplate(t.getRegisterSubject(), t.getRegisterHtml(),
                        MailTemplateDefaults.REGISTER_SUBJECT, MailTemplateDefaults.REGISTER_HTML))
                .reset(resolveTemplate(t.getResetSubject(), t.getResetHtml(),
                        MailTemplateDefaults.RESET_SUBJECT, MailTemplateDefaults.RESET_HTML))
                .welcome(resolveTemplate(t.getWelcomeSubject(), t.getWelcomeHtml(),
                        MailTemplateDefaults.WELCOME_SUBJECT, MailTemplateDefaults.WELCOME_HTML))
                .build();
    }

    private static AdminSettingVO.MailTemplate resolveTemplate(
            String customSubject, String customHtml, String defaultSubject, String defaultHtml) {
        boolean usingDefault = !StringUtils.hasText(customSubject) && !StringUtils.hasText(customHtml);
        return AdminSettingVO.MailTemplate.builder()
                .subject(StringUtils.hasText(customSubject) ? customSubject : defaultSubject)
                .html(StringUtils.hasText(customHtml) ? customHtml : defaultHtml)
                .usingDefault(usingDefault)
                .build();
    }

    @Override
    @Transactional
    public AdminSettingVO updateAdminSide(AdminSideSettingUpdateDTO dto, Long operatorId) {
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setAdminCaptchaEnabled(Boolean.TRUE.equals(dto.getCaptchaEnabled()));
        row.setUpdateBy(operatorId);
        persist(row);
        applyAdminSide(row);
        return getSettings();
    }

    @Override
    @Transactional
    public AdminSettingVO updateClientSide(ClientSideSettingUpdateDTO dto, Long operatorId) {
        String appVersion = dto.getAppVersion().trim();
        String minSupported = nullToEmpty(dto.getMinSupportedVersion());
        if (StringUtils.hasText(minSupported) && AppVersionUtils.compare(minSupported, appVersion) > 0) {
            throw new CustomException(400, "最低支持版本不能高于应用版本");
        }
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setClientCaptchaEnabled(Boolean.TRUE.equals(dto.getCaptchaEnabled()));
        row.setAppVersion(appVersion);
        row.setAppChannel(AppVersionUtils.normalizeChannel(dto.getAppChannel()));
        row.setReleaseNotes(nullToEmpty(dto.getReleaseNotes()));
        row.setDownloadUrl(nullToEmpty(dto.getDownloadUrl()));
        row.setForceUpdate(Boolean.TRUE.equals(dto.getForceUpdate()));
        row.setMinSupportedVersion(minSupported);
        row.setMaxUploadBytes(dto.getMaxUploadBytes());
        row.setSensitiveFilterEnabled(Boolean.TRUE.equals(dto.getSensitiveFilterEnabled()));
        row.setSupportEmail(nullToEmpty(dto.getSupportEmail()));
        row.setSupportPhone(nullToEmpty(dto.getSupportPhone()));
        row.setFeedbackSlaHours(dto.getFeedbackSlaHours());
        row.setFeedbackEscalationEnabled(Boolean.TRUE.equals(dto.getFeedbackEscalationEnabled()));
        row.setFeedbackEscalationAutoReassign(Boolean.TRUE.equals(dto.getFeedbackEscalationAutoReassign()));
        row.setFeedbackEscalationIntervalHours(dto.getFeedbackEscalationIntervalHours());
        row.setReviewSlaHours(dto.getReviewSlaHours());
        row.setReviewEscalationEnabled(Boolean.TRUE.equals(dto.getReviewEscalationEnabled()));
        row.setReviewEscalationIntervalHours(dto.getReviewEscalationIntervalHours());
        row.setUpdateBy(operatorId);
        persist(row);
        applyClientSide(row);
        return getSettings();
    }

    @Override
    @Transactional
    public AdminSettingVO updateRegister(RegisterSettingUpdateDTO dto, Long operatorId) {
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setClientRegisterEnabled(Boolean.TRUE.equals(dto.getRegisterEnabled()));
        row.setClientForgotPasswordEmailEnabled(Boolean.TRUE.equals(dto.getForgotPasswordEmailEnabled()));
        row.setUpdateBy(operatorId);
        persist(row);
        applyRegisterSide(row);
        return getSettings();
    }

    @Override
    @Transactional
    public AdminSettingVO updateLogin(LoginSettingUpdateDTO dto, Long operatorId) {
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        LoginSettingUpdateDTO.Side client = dto.getClient();
        LoginSettingUpdateDTO.Side admin = dto.getAdmin();
        boolean totpRequired = Boolean.TRUE.equals(admin.getTotpRequired());
        boolean captchaEnabled = Boolean.TRUE.equals(admin.getCaptchaEnabled());
        if (isProdProfile()) {
            if (!totpRequired) {
                throw new CustomException(400, "生产环境不允许关闭管理端强制 TOTP");
            }
            if (!captchaEnabled) {
                throw new CustomException(400, "生产环境不允许关闭管理端登录验证码");
            }
        }
        row.setClientCaptchaEnabled(Boolean.TRUE.equals(client.getCaptchaEnabled()));
        row.setClientLoginMaxAttempts(client.getMaxAttempts());
        row.setClientLockDurationMinutes(client.getLockDurationMinutes());
        row.setAdminCaptchaEnabled(captchaEnabled);
        row.setAdminLoginMaxAttempts(admin.getMaxAttempts());
        row.setAdminLockDurationMinutes(admin.getLockDurationMinutes());
        row.setAdminTotpRequired(totpRequired);
        row.setUpdateBy(operatorId);
        persist(row);
        applyLoginSide(row);
        return getSettings();
    }

    private boolean isProdProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public AdminSettingVO updatePassword(PasswordSettingUpdateDTO dto, Long operatorId) {
        if (dto.getMinLength() > dto.getMaxLength()) {
            throw new CustomException(400, "密码最小长度不能大于最大长度");
        }
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setPasswordMinLength(dto.getMinLength());
        row.setPasswordMaxLength(dto.getMaxLength());
        row.setPasswordRequireUpperLower(Boolean.TRUE.equals(dto.getRequireUpperLower()));
        row.setPasswordRequireDigit(Boolean.TRUE.equals(dto.getRequireDigit()));
        row.setPasswordRequireSpecial(Boolean.TRUE.equals(dto.getRequireSpecial()));
        row.setUpdateBy(operatorId);
        persist(row);
        applyPasswordSide(row);
        return getSettings();
    }

    @Override
    @Transactional
    public AdminSettingVO updateMail(MailSettingUpdateDTO dto, Long operatorId) {
        if (Boolean.TRUE.equals(dto.getStartTls()) && Boolean.TRUE.equals(dto.getSsl())) {
            throw new CustomException(400, "STARTTLS 与 SSL 不能同时开启");
        }
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setMailHost(dto.getHost().trim());
        row.setMailPort(dto.getPort());
        row.setMailUsername(nullToEmpty(dto.getUsername()));
        if (StringUtils.hasText(dto.getPassword())) {
            row.setMailPassword(dto.getPassword().trim());
        } else if (row.getMailPassword() == null) {
            // 首次写入且未填密码：沿用当前内存配置（通常来自 env）
            row.setMailPassword(nullToEmpty(linkxProperties.getMail().getPassword()));
        }
        row.setMailFrom(dto.getFrom().trim());
        row.setMailFromName(nullToEmpty(dto.getFromName()));
        row.setMailStartTls(Boolean.TRUE.equals(dto.getStartTls()));
        row.setMailSsl(Boolean.TRUE.equals(dto.getSsl()));
        row.setMailCodeExpireMinutes(dto.getCodeExpireMinutes());
        row.setUpdateBy(operatorId);
        persist(row);
        applyMailSide(row);
        mailSenderHolder.reload();
        return getSettings();
    }

    @Override
    @Transactional
    public AdminSettingVO updateMailTemplates(MailTemplateSettingUpdateDTO dto, Long operatorId) {
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        // 空字符串表示恢复内置默认；写入空串避免 MyBatis 忽略 null 字段
        row.setMailTplRegisterSubject(nullToEmpty(dto.getRegister().getSubject()));
        row.setMailTplRegisterHtml(nullToEmpty(dto.getRegister().getHtml()));
        row.setMailTplResetSubject(nullToEmpty(dto.getReset().getSubject()));
        row.setMailTplResetHtml(nullToEmpty(dto.getReset().getHtml()));
        row.setMailTplWelcomeSubject(nullToEmpty(dto.getWelcome().getSubject()));
        row.setMailTplWelcomeHtml(nullToEmpty(dto.getWelcome().getHtml()));
        row.setUpdateBy(operatorId);
        persist(row);
        applyMailTemplates(row);
        return getSettings();
    }

    @Override
    @Transactional
    public AdminSettingVO updateSecurity(SecuritySettingUpdateDTO dto, Long operatorId) {
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setDisableFrontendDebug(Boolean.TRUE.equals(dto.getDisableFrontendDebug()));
        if (dto.getApiSignEnabled() != null) {
            row.setApiSignEnabled(Boolean.TRUE.equals(dto.getApiSignEnabled()));
        }
        if (dto.getApiEncryptEnabled() != null) {
            boolean encrypt = Boolean.TRUE.equals(dto.getApiEncryptEnabled());
            row.setApiEncryptEnabled(encrypt);
            if (encrypt) {
                row.setApiSignEnabled(true);
            }
        }
        row.setUpdateBy(operatorId);
        persist(row);
        applySecuritySide(row);
        return getSettings();
    }

    @Override
    @Transactional
    public void syncPublishedAppVersion(String version,
                                        String channel,
                                        String releaseNotes,
                                        String downloadUrl,
                                        Boolean forceUpdate,
                                        String minSupportedVersion,
                                        Long operatorId) {
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        row.setAppVersion(version.trim());
        row.setAppChannel(AppVersionUtils.normalizeChannel(channel));
        row.setReleaseNotes(nullToEmpty(releaseNotes));
        row.setDownloadUrl(nullToEmpty(downloadUrl));
        row.setForceUpdate(Boolean.TRUE.equals(forceUpdate));
        row.setMinSupportedVersion(nullToEmpty(minSupportedVersion));
        row.setUpdateBy(operatorId);
        persist(row);
        applyClientSide(row);
        log.info("Synced published app version {} ({}) to runtime settings", version, channel);
    }

    @Override
    public String testForgotPasswordEmail(String email) {
        String target = email == null ? "" : email.trim().toLowerCase();
        if (!StringUtils.hasText(target)) {
            throw new CustomException(400, "请填写测试收件邮箱");
        }
        try {
            // 固定测试验证码，仅用于连通性校验，不会写入 Redis，不可用于真实重置
            emailService.sendPasswordResetCode(target, "admin-test", "000000");
            return "测试邮件已发送，请查收收件箱（验证码为 000000，仅作连通性测试）";
        } catch (CustomException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "邮件发送失败";
            throw new CustomException(500, "测试未通过：" + msg);
        }
    }

    private SysRuntimeSetting loadOrCreateRow(Long operatorId) {
        SysRuntimeSetting existing = runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID);
        if (existing != null) {
            return existing;
        }
        // 首次写入：用当前内存配置填充整行，避免另一侧被默认值覆盖
        LinkxProperties.App app = linkxProperties.getApp();
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return SysRuntimeSetting.builder()
                .id(SysRuntimeSetting.SINGLETON_ID)
                .adminCaptchaEnabled(auth.isAdminCaptchaEnabled())
                .adminLoginMaxAttempts(auth.getAdminLoginMaxAttempts())
                .adminLockDurationMinutes(auth.getAdminLockDurationMinutes())
                .adminTotpRequired(auth.isAdminTotpRequired())
                .clientCaptchaEnabled(auth.isCaptchaEnabled())
                .clientRegisterEnabled(auth.isRegisterEnabled())
                .clientForgotPasswordEmailEnabled(auth.isForgotPasswordEmailEnabled())
                .clientLoginMaxAttempts(auth.getLoginMaxAttempts())
                .clientLockDurationMinutes(auth.getLockDurationMinutes())
                .passwordMinLength(auth.getPasswordMinLength())
                .passwordMaxLength(auth.getPasswordMaxLength())
                .passwordRequireUpperLower(auth.isPasswordRequireUpperLower())
                .passwordRequireDigit(auth.isPasswordRequireDigit())
                .passwordRequireSpecial(auth.isPasswordRequireSpecial())
                .appVersion(app != null && StringUtils.hasText(app.getVersion()) ? app.getVersion() : "1.0.0")
                .appChannel(app != null && StringUtils.hasText(app.getChannel()) ? app.getChannel() : "stable")
                .releaseNotes(app != null ? nullToEmpty(app.getReleaseNotes()) : "")
                .downloadUrl(app != null ? nullToEmpty(app.getDownloadUrl()) : "")
                .forceUpdate(app != null && Boolean.TRUE.equals(app.getForceUpdate()))
                .minSupportedVersion(app != null ? nullToEmpty(app.getMinSupportedVersion()) : "")
                .maxUploadBytes(linkxProperties.getMinio().getMaxFileSize())
                .sensitiveFilterEnabled(app == null || !Boolean.FALSE.equals(app.getSensitiveFilterEnabled()))
                .supportEmail(app != null ? nullToEmpty(app.getSupportEmail()) : "")
                .supportPhone(app != null ? nullToEmpty(app.getSupportPhone()) : "")
                .feedbackSlaHours(resolveFeedbackSlaHours(app))
                .feedbackEscalationEnabled(resolveFeedbackEscalationEnabled(app))
                .feedbackEscalationAutoReassign(resolveFeedbackEscalationAutoReassign(app))
                .feedbackEscalationIntervalHours(resolveFeedbackEscalationIntervalHours(app))
                .reviewSlaHours(resolveReviewSlaHours(app))
                .reviewEscalationEnabled(resolveReviewEscalationEnabled(app))
                .reviewEscalationIntervalHours(resolveReviewEscalationIntervalHours(app))
                .mailHost(linkxProperties.getMail().getHost())
                .mailPort(linkxProperties.getMail().getPort())
                .mailUsername(nullToEmpty(linkxProperties.getMail().getUsername()))
                .mailPassword(nullToEmpty(linkxProperties.getMail().getPassword()))
                .mailFrom(linkxProperties.getMail().getFrom())
                .mailFromName(nullToEmpty(linkxProperties.getMail().getFromName()))
                .mailStartTls(linkxProperties.getMail().isStartTls())
                .mailSsl(linkxProperties.getMail().isSsl())
                .mailCodeExpireMinutes(linkxProperties.getMail().getCodeExpireMinutes())
                .mailTplRegisterSubject(trimOrNull(linkxProperties.getMailTemplates().getRegisterSubject()))
                .mailTplRegisterHtml(trimOrNull(linkxProperties.getMailTemplates().getRegisterHtml()))
                .mailTplResetSubject(trimOrNull(linkxProperties.getMailTemplates().getResetSubject()))
                .mailTplResetHtml(trimOrNull(linkxProperties.getMailTemplates().getResetHtml()))
                .mailTplWelcomeSubject(trimOrNull(linkxProperties.getMailTemplates().getWelcomeSubject()))
                .mailTplWelcomeHtml(trimOrNull(linkxProperties.getMailTemplates().getWelcomeHtml()))
                .updateBy(operatorId)
                .build();
    }

    private void persist(SysRuntimeSetting row) {
        SysRuntimeSetting existing = runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID);
        if (existing == null) {
            runtimeSettingMapper.insert(row);
        } else {
            runtimeSettingMapper.update(row);
        }
    }

    private void applyToProperties(SysRuntimeSetting row) {
        applyAdminSide(row);
        applyClientSide(row);
        applyRegisterSide(row);
        applyLoginSide(row);
        applyPasswordSide(row);
        applyRiskPolicySide(row);
        applyMailSide(row);
        applyMailTemplates(row);
        applySecuritySide(row);
        if (row.getMailHost() != null) {
            mailSenderHolder.reload();
        }
    }

    private void applyMailSide(SysRuntimeSetting row) {
        LinkxProperties.Mail mail = linkxProperties.getMail();
        if (row.getMailHost() != null) {
            mail.setHost(nullToEmpty(row.getMailHost()));
        }
        if (row.getMailPort() != null && row.getMailPort() > 0) {
            mail.setPort(row.getMailPort());
        }
        if (row.getMailUsername() != null) {
            mail.setUsername(nullToEmpty(row.getMailUsername()));
        }
        if (row.getMailPassword() != null) {
            mail.setPassword(row.getMailPassword());
        }
        if (row.getMailFrom() != null) {
            mail.setFrom(nullToEmpty(row.getMailFrom()));
        }
        if (row.getMailFromName() != null) {
            mail.setFromName(nullToEmpty(row.getMailFromName()));
        }
        if (row.getMailStartTls() != null) {
            mail.setStartTls(row.getMailStartTls());
        }
        if (row.getMailSsl() != null) {
            mail.setSsl(row.getMailSsl());
        }
        if (row.getMailCodeExpireMinutes() != null && row.getMailCodeExpireMinutes() > 0) {
            mail.setCodeExpireMinutes(row.getMailCodeExpireMinutes());
        }
        log.info("Applied mail settings: host={}, port={}, tls={}, ssl={}, expireMin={}",
                mail.getHost(), mail.getPort(), mail.isStartTls(), mail.isSsl(), mail.getCodeExpireMinutes());
    }

    private void applyMailTemplates(SysRuntimeSetting row) {
        LinkxProperties.MailTemplates t = linkxProperties.getMailTemplates();
        t.setRegisterSubject(nullToEmpty(row.getMailTplRegisterSubject()));
        t.setRegisterHtml(nullToEmpty(row.getMailTplRegisterHtml()));
        t.setResetSubject(nullToEmpty(row.getMailTplResetSubject()));
        t.setResetHtml(nullToEmpty(row.getMailTplResetHtml()));
        t.setWelcomeSubject(nullToEmpty(row.getMailTplWelcomeSubject()));
        t.setWelcomeHtml(nullToEmpty(row.getMailTplWelcomeHtml()));
    }

    private void applyAdminSide(SysRuntimeSetting row) {
        if (row.getAdminCaptchaEnabled() != null) {
            linkxProperties.getAuth().setAdminCaptchaEnabled(row.getAdminCaptchaEnabled());
        }
    }

    private void applyClientSide(SysRuntimeSetting row) {
        if (row.getClientCaptchaEnabled() != null) {
            linkxProperties.getAuth().setCaptchaEnabled(row.getClientCaptchaEnabled());
        }
        LinkxProperties.App app = linkxProperties.getApp();
        if (StringUtils.hasText(row.getAppVersion())) {
            app.setVersion(row.getAppVersion());
        }
        if (StringUtils.hasText(row.getAppChannel())) {
            app.setChannel(row.getAppChannel());
        }
        if (row.getReleaseNotes() != null) {
            app.setReleaseNotes(nullToEmpty(row.getReleaseNotes()));
        }
        if (row.getDownloadUrl() != null) {
            app.setDownloadUrl(nullToEmpty(row.getDownloadUrl()));
        }
        if (row.getForceUpdate() != null) {
            app.setForceUpdate(row.getForceUpdate());
        }
        if (row.getMinSupportedVersion() != null) {
            app.setMinSupportedVersion(nullToEmpty(row.getMinSupportedVersion()));
        }
        if (row.getMaxUploadBytes() != null && row.getMaxUploadBytes() > 0) {
            linkxProperties.getMinio().setMaxFileSize(row.getMaxUploadBytes());
        }
        if (row.getSensitiveFilterEnabled() != null) {
            app.setSensitiveFilterEnabled(row.getSensitiveFilterEnabled());
        }
        if (row.getSupportEmail() != null) {
            app.setSupportEmail(nullToEmpty(row.getSupportEmail()));
        }
        if (row.getSupportPhone() != null) {
            app.setSupportPhone(nullToEmpty(row.getSupportPhone()));
        }
        if (row.getFeedbackSlaHours() != null && row.getFeedbackSlaHours() > 0) {
            app.setFeedbackSlaHours(Math.min(row.getFeedbackSlaHours(), 720));
        }
        if (row.getFeedbackEscalationEnabled() != null) {
            app.setFeedbackEscalationEnabled(row.getFeedbackEscalationEnabled());
        }
        if (row.getFeedbackEscalationAutoReassign() != null) {
            app.setFeedbackEscalationAutoReassign(row.getFeedbackEscalationAutoReassign());
        }
        if (row.getFeedbackEscalationIntervalHours() != null && row.getFeedbackEscalationIntervalHours() > 0) {
            app.setFeedbackEscalationIntervalHours(Math.min(row.getFeedbackEscalationIntervalHours(), 720));
        }
        if (row.getReviewSlaHours() != null && row.getReviewSlaHours() > 0) {
            app.setReviewSlaHours(Math.min(row.getReviewSlaHours(), 720));
        }
        if (row.getReviewEscalationEnabled() != null) {
            app.setReviewEscalationEnabled(row.getReviewEscalationEnabled());
        }
        if (row.getReviewEscalationIntervalHours() != null && row.getReviewEscalationIntervalHours() > 0) {
            app.setReviewEscalationIntervalHours(Math.min(row.getReviewEscalationIntervalHours(), 720));
        }
    }

    private void applyRegisterSide(SysRuntimeSetting row) {
        if (row.getClientRegisterEnabled() != null) {
            linkxProperties.getAuth().setRegisterEnabled(row.getClientRegisterEnabled());
        }
        if (row.getClientForgotPasswordEmailEnabled() != null) {
            linkxProperties.getAuth().setForgotPasswordEmailEnabled(row.getClientForgotPasswordEmailEnabled());
        }
    }

    private void applyLoginSide(SysRuntimeSetting row) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        if (row.getClientCaptchaEnabled() != null) {
            auth.setCaptchaEnabled(row.getClientCaptchaEnabled());
        }
        if (row.getAdminCaptchaEnabled() != null) {
            auth.setAdminCaptchaEnabled(row.getAdminCaptchaEnabled());
        }
        if (row.getClientLoginMaxAttempts() != null) {
            auth.setLoginMaxAttempts(row.getClientLoginMaxAttempts());
        }
        if (row.getAdminLoginMaxAttempts() != null) {
            auth.setAdminLoginMaxAttempts(row.getAdminLoginMaxAttempts());
        }
        if (row.getClientLockDurationMinutes() != null) {
            auth.setLockDurationMinutes(row.getClientLockDurationMinutes());
        }
        if (row.getAdminLockDurationMinutes() != null) {
            auth.setAdminLockDurationMinutes(row.getAdminLockDurationMinutes());
        }
        if (row.getAdminTotpRequired() != null) {
            auth.setAdminTotpRequired(row.getAdminTotpRequired());
        }
        log.info("Applied login settings: client(captcha={}, maxAttempts={}, lockMin={}), admin(captcha={}, maxAttempts={}, lockMin={}, totpRequired={})",
                auth.isCaptchaEnabled(), auth.getLoginMaxAttempts(), auth.getLockDurationMinutes(),
                auth.isAdminCaptchaEnabled(), auth.getAdminLoginMaxAttempts(), auth.getAdminLockDurationMinutes(),
                auth.isAdminTotpRequired());
    }

    private void applyPasswordSide(SysRuntimeSetting row) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        if (row.getPasswordMinLength() != null) {
            auth.setPasswordMinLength(row.getPasswordMinLength());
        }
        if (row.getPasswordMaxLength() != null) {
            auth.setPasswordMaxLength(row.getPasswordMaxLength());
        }
        if (row.getPasswordRequireUpperLower() != null) {
            auth.setPasswordRequireUpperLower(row.getPasswordRequireUpperLower());
        }
        if (row.getPasswordRequireDigit() != null) {
            auth.setPasswordRequireDigit(row.getPasswordRequireDigit());
        }
        if (row.getPasswordRequireSpecial() != null) {
            auth.setPasswordRequireSpecial(row.getPasswordRequireSpecial());
        }
        log.info("Applied password policy: min={}, max={}, upperLower={}, digit={}, special={}",
                auth.getPasswordMinLength(), auth.getPasswordMaxLength(),
                auth.isPasswordRequireUpperLower(), auth.isPasswordRequireDigit(), auth.isPasswordRequireSpecial());
    }

    private void applyRiskPolicySide(SysRuntimeSetting row) {
        LinkxProperties.RiskPolicy policy = linkxProperties.getRiskPolicy();
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        if (row.getRiskStormUserThreshold() != null) {
            policy.setMessageStormUserThreshold(row.getRiskStormUserThreshold());
        }
        if (row.getRiskStormUserWindowSeconds() != null) {
            policy.setMessageStormUserWindowSeconds(row.getRiskStormUserWindowSeconds());
        }
        if (row.getRiskStormGroupMinMembers() != null) {
            policy.setMessageStormGroupMinMembers(row.getRiskStormGroupMinMembers());
        }
        if (row.getRiskStormGroupLargeMembers() != null) {
            policy.setMessageStormGroupLargeMemberThreshold(row.getRiskStormGroupLargeMembers());
        }
        if (row.getRiskStormGroupMidPerMinute() != null) {
            policy.setMessageStormGroupMidMaxPerMinute(row.getRiskStormGroupMidPerMinute());
        }
        if (row.getRiskStormGroupLargePerMinute() != null) {
            policy.setMessageStormGroupLargeMaxPerMinute(row.getRiskStormGroupLargePerMinute());
        }
        if (row.getRiskScoreMediumMin() != null) {
            policy.setScoreMediumMin(row.getRiskScoreMediumMin());
        }
        if (row.getRiskScoreHighMin() != null) {
            policy.setScoreHighMin(row.getRiskScoreHighMin());
        }
        if (row.getRiskScoreCriticalMin() != null) {
            policy.setScoreCriticalMin(row.getRiskScoreCriticalMin());
        }
        if (row.getRateLimitLoginPerMinute() != null) {
            auth.setRateLimitLoginPerMinute(row.getRateLimitLoginPerMinute());
        }
        if (row.getRateLimitRegisterPerMinute() != null) {
            auth.setRateLimitRegisterPerMinute(row.getRateLimitRegisterPerMinute());
        }
        if (row.getRateLimitSearchPerMinute() != null) {
            auth.setRateLimitSearchPerMinute(row.getRateLimitSearchPerMinute());
        }
        if (row.getRateLimitListPerMinute() != null) {
            auth.setRateLimitListPerMinute(row.getRateLimitListPerMinute());
        }
        if (row.getRateLimitWritePerMinute() != null) {
            auth.setRateLimitWritePerMinute(row.getRateLimitWritePerMinute());
        }
        if (row.getRateLimitUploadPerMinute() != null) {
            auth.setRateLimitUploadPerMinute(row.getRateLimitUploadPerMinute());
        }
    }

    private void applySecuritySide(SysRuntimeSetting row) {
        LinkxProperties.Security security = linkxProperties.getSecurity();
        if (row.getDisableFrontendDebug() != null) {
            security.setDisableFrontendDebug(row.getDisableFrontendDebug());
        }
        if (row.getApiSignEnabled() != null) {
            security.setApiSignEnabled(row.getApiSignEnabled());
        }
        if (row.getApiEncryptEnabled() != null) {
            security.setApiEncryptEnabled(row.getApiEncryptEnabled());
            if (Boolean.TRUE.equals(row.getApiEncryptEnabled())) {
                security.setApiSignEnabled(true);
            }
        }
        log.info("Applied security settings: apiSign={}, apiEncrypt={}, disableFrontendDebug={}",
                security.isApiSignEnabled(), security.isApiEncryptEnabled(), security.isDisableFrontendDebug());
    }

    private static String nullToEmpty(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
