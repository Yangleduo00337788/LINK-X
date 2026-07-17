package com.linkx.server.service;

import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

public interface SysUserService extends IService<SysUser> {

    void register(RegisterDTO registerDTO, HttpServletRequest request);

    TokenVO login(LoginDTO loginDTO, String ip, String userAgent, HttpServletRequest request);

    /**
     * 更新用户资料
     *
     * @param userId 用户 ID
     * @param dto    更新内容
     * @return 更新后的用户
     */
    SysUser updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 更新用户头像
     *
     * @param userId    用户 ID
     * @param avatarUrl 头像 URL
     */
    void updateAvatar(Long userId, String avatarUrl);

    /**
     * 修改密码
     *
     * @param userId      用户 ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 重置密码（通过验证码，验证码已与账号绑定）
     *
     * @param userId      用户 ID（从 token 获取，防越权）
     * @param captchaId    验证码ID
     * @param captchaCode 验证码
     * @param newPassword 新密码
     */
    void resetPassword(Long userId, String captchaId, String captchaCode, String newPassword);
}
