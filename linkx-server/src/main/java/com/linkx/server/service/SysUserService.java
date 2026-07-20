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
     * 更新友链背景图（存储在 user_preference 表）
     *
     * @param userId    用户 ID
     * @param objectKey MinIO 对象 key
     */
    void updateMomentsBackground(Long userId, String objectKey);

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

    /**
     * 通过用户名查找用户邮箱
     *
     * @param username 用户名
     * @return 用户邮箱，未设置则返回 null
     */
    String findEmailByUsername(String username);

    /**
     * 发送密码重置邮件验证码
     *
     * @param username 用户名
     * @param ip       客户端 IP
     */
    void sendPasswordResetEmailCode(String username, String ip);

    /**
     * 通过邮箱验证码重置密码
     *
     * @param username    用户名
     * @param code        验证码
     * @param newPassword 新密码
     * @param ip          客户端 IP
     */
    void resetPasswordByEmail(String username, String code, String newPassword, String ip);

    /**
     * 仅校验邮箱验证码，不消费（前端可分两步：先校验、再重置）。
     * <p>
     * 注意：这里采用「软校验」——只读 Redis、比较、返回结果，不删除 key。
     * 真正的 key 删除发生在 resetPasswordByEmail 调用时，避免校验通过却未提交重置导致验证码流失。
     * 但为防爆破，这里仍然在 IP 维度做限流（与 resetPasswordByEmail 共享桶）。
     *
     * @param username 用户名
     * @param code     验证码
     * @param ip       客户端 IP
     */
    void verifyEmailResetCode(String username, String code, String ip);
}
