package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.ClientSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.LoginSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.PasswordSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.RegisterSettingUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;

public interface AdminSettingService {

    AdminSettingVO getSettings();

    AdminSettingVO updateAdminSide(AdminSideSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateClientSide(ClientSideSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateRegister(RegisterSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updateLogin(LoginSettingUpdateDTO dto, Long operatorId);

    AdminSettingVO updatePassword(PasswordSettingUpdateDTO dto, Long operatorId);

    /**
     * 发送一封测试用的「忘记密码」邮件，用于校验 SMTP 是否可用。
     *
     * @return 成功时的提示文案
     */
    String testForgotPasswordEmail(String email);
}
