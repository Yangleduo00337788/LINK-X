package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.dto.AdminSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.ClientSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.LoginSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.MailTemplateSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.PasswordSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.RegisterSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.SecuritySettingUpdateDTO;
import com.linkx.server.controller.admin.dto.StorageSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.TestStorageConnectionDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;

public interface AdminSettingService {

    AdminSettingVO getSettings();

    AdminSettingVO updateSettings(AdminSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateAdminSide(AdminSideSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateClientSide(ClientSideSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateRegister(RegisterSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateLogin(LoginSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updatePassword(PasswordSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateMail(MailSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateMailTemplates(MailTemplateSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateSecurity(SecuritySettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateStorage(StorageSettingUpdateDTO dto, Long operatorId);

    /**
     * 发送一封测试用的「忘记密码」邮件，用于校验 SMTP 是否可用。
     *
     * @return 成功时的提示文案
     */
    String testForgotPasswordEmail(String email);

    /**
     * 测试对象存储连通性（使用表单参数，不一定落库）。
     */
    String testStorageConnection(TestStorageConnectionDTO dto);

    /**
     * 将已发布版本同步到运行时配置（供客户端检查更新）。
     */
    void syncPublishedAppVersion(String version,
                                 String channel,
                                 String releaseNotes,
                                 String downloadUrl,
                                 Boolean forceUpdate,
                                 String minSupportedVersion,
                                 Long operatorId);
}
