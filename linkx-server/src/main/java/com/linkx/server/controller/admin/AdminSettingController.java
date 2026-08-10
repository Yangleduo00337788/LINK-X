package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireStepUp;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.ClientSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailTemplateSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.RegisterSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.SecuritySettingUpdateDTO;
import com.linkx.server.controller.admin.dto.TestForgotPasswordEmailDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.admin.AdminSettingService;
import com.linkx.server.service.admin.AdminStepUpService;
import com.linkx.server.service.admin.impl.AdminStepUpServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-系统配置")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminSettingController {

    private final AdminSettingService adminSettingService;
    private final AdminStepUpService adminStepUpService;

    @Operation(summary = "查询系统配置")
    @GetMapping
    @RequirePermission("admin:setting:view")
    public Result<AdminSettingVO> get() {
        return Result.success(adminSettingService.getSettings());
    }

    @Operation(summary = "统一更新系统配置（仅提交需变更的分组）")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新系统配置")
    @PutMapping
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> update(@Valid @RequestBody AdminSettingUpdateDTO dto,
                                         HttpServletRequest request) {
        enforceStepUpIfNeeded(dto, request);
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateSettings(dto, operatorId));
    }

    @Operation(summary = "更新管理端配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新管理端配置")
    @PutMapping("/admin")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateAdmin(@Valid @RequestBody AdminSideSettingUpdateDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateAdminSide(dto, operatorId));
    }

    @Operation(summary = "更新客户端配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新客户端配置")
    @PutMapping("/client")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateClient(@Valid @RequestBody ClientSideSettingUpdateDTO dto,
                                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateClientSide(dto, operatorId));
    }

    @Operation(summary = "更新注册配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新注册配置")
    @PutMapping("/register")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateRegister(@Valid @RequestBody RegisterSettingUpdateDTO dto,
                                                 HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateRegister(dto, operatorId));
    }

    @Operation(summary = "更新登录配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新登录配置")
    @PutMapping("/login")
    @RequirePermission("admin:setting:edit")
    @RequireStepUp("admin:setting:edit")
    public Result<AdminSettingVO> updateLogin(@Valid @RequestBody com.linkx.server.controller.admin.dto.LoginSettingUpdateDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateLogin(dto, operatorId));
    }

    @Operation(summary = "更新密码策略")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新密码策略")
    @PutMapping("/password")
    @RequirePermission("admin:setting:edit")
    @RequireStepUp("admin:setting:edit")
    public Result<AdminSettingVO> updatePassword(
            @Valid @RequestBody com.linkx.server.controller.admin.dto.PasswordSettingUpdateDTO dto,
            HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updatePassword(dto, operatorId));
    }

    @Operation(summary = "更新邮件 SMTP 配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新邮件配置")
    @PutMapping("/mail")
    @RequirePermission("admin:setting:edit")
    @RequireStepUp("admin:setting:edit")
    public Result<AdminSettingVO> updateMail(@Valid @RequestBody MailSettingUpdateDTO dto,
                                             HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateMail(dto, operatorId));
    }

    @Operation(summary = "更新邮件模板")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新邮件模板")
    @PutMapping("/mail-templates")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateMailTemplates(@Valid @RequestBody MailTemplateSettingUpdateDTO dto,
                                                      HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateMailTemplates(dto, operatorId));
    }

    @Operation(summary = "更新安全配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新安全配置")
    @PutMapping("/security")
    @RequirePermission("admin:setting:edit")
    @RequireStepUp("admin:setting:edit")
    public Result<AdminSettingVO> updateSecurity(@Valid @RequestBody SecuritySettingUpdateDTO dto,
                                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateSecurity(dto, operatorId));
    }

    @Operation(summary = "测试忘记密码邮件")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "测试忘记密码邮件")
    @PostMapping("/test-forgot-password-email")
    @RequirePermission("admin:setting:edit")
    public Result<String> testForgotPasswordEmail(@Valid @RequestBody TestForgotPasswordEmailDTO dto) {
        return Result.success(adminSettingService.testForgotPasswordEmail(dto.getEmail()));
    }

    private void enforceStepUpIfNeeded(AdminSettingUpdateDTO dto, HttpServletRequest request) {
        if (dto == null || !requiresSensitiveStepUp(dto) || !adminStepUpService.isEnabled()) {
            return;
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        String token = request.getHeader(AdminStepUpServiceImpl.HEADER);
        if (!StringUtils.hasText(token)
                || !adminStepUpService.consumeToken(userId, token, "admin:setting:edit")) {
            var options = adminStepUpService.options(userId, "admin:setting:edit");
            if (options.getMethods() == null || options.getMethods().isEmpty()) {
                throw new CustomException(403, "高危操作需二次验证，请先启用 TOTP 或绑定邮箱");
            }
            throw new CustomException(
                    AdminStepUpServiceImpl.CODE_STEP_UP_REQUIRED,
                    "需要二次验证",
                    options);
        }
    }

    private static boolean requiresSensitiveStepUp(AdminSettingUpdateDTO dto) {
        return dto.getLogin() != null || dto.getPassword() != null || dto.getMail() != null
                || dto.getSecurity() != null;
    }
}
