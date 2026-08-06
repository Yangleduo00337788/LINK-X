package com.linkx.server.service;

import com.linkx.server.common.LoginSide;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

public interface SysUserService extends IService<SysUser> {

    void register(RegisterDTO registerDTO, HttpServletRequest request);

    /**
     * 发送客户端注册邮箱验证码。
     */
    void sendRegisterEmailCode(String email, String username, String ip);

    TokenVO login(LoginDTO loginDTO, String ip, String userAgent, HttpServletRequest request);

    /**
     * 校验用户名密码与账号状态，不签发令牌、不写设备会话。
     * 供管理端等需在签发前做额外授权检查的场景使用。
     *
     * @param side 登录入口（客户端 / 管理端），决定失败阈值与锁定配置
     */
    SysUser verifyCredentials(LoginDTO loginDTO, String ip, String userAgent, HttpServletRequest request, LoginSide side);

    /**
     * 在凭证已通过校验后建立设备会话并签发令牌。
     */
    TokenVO establishSession(SysUser user, String ip, String userAgent, HttpServletRequest request);

    /**
     * 将到期的自动封禁账号解封（status 禁用→启用）。可由定时任务或登录前调用。
     *
     * @return 解封账号数
     */
    int unlockExpiredAutoLocks();

    /**
     * 记录一次登录失败（密码错误 / 验证码错误等），达到阈值时自动禁用账号并抛出 429。
     */
    void onLoginFailure(String username, HttpServletRequest request, LoginSide side);


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

    /**
     * 向新邮箱发送绑定验证码
     */
    void sendBindEmailCode(Long userId, String email, String ip);

    /**
     * 校验验证码并绑定邮箱
     */
    void bindEmail(Long userId, String email, String code, String ip);

    /**
     * 绑定手机号（需验证登录密码）
     */
    void bindPhone(Long userId, String phone, String password);

    /**
     * 修改 LinkX ID（登录用户名）
     */
    SysUser changeUsername(Long userId, String newUsername, String password);

    /**
     * 注销账号（逻辑删除 + 吊销全部 Token）
     */
    void deleteAccount(Long userId, String password);
}
