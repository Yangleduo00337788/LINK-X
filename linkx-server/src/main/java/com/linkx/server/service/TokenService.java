package com.linkx.server.service;

import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;

public interface TokenService {

    TokenVO issueTokenPair(SysUser user);

    /** 签发令牌并绑定到设备（用于按设备踢下线）。 */
    TokenVO issueTokenPair(SysUser user, String deviceId);

    TokenVO refreshAccessToken(String refreshToken);

    /** 刷新令牌并重新绑定设备。 */
    TokenVO refreshAccessToken(String refreshToken, String deviceId);

    /**
     * 登出。
     *
     * @param accessToken 必须的当前 access token（用于校验身份）
     * @param refreshToken 可选的 refresh token（同一用户时会被一并吊销）
     * @throws com.linkx.server.exception.CustomException 当 accessToken 无效、
     *         或 refreshToken 所属用户与 accessToken 不一致时
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 吊销用户的所有 Token（用于密码重置后强制下线）
     *
     * @param userId 用户 ID
     */
    void revokeAllUserTokens(Long userId);

    /**
     * 吊销指定设备上的 access/refresh，并标记该设备已踢下线。
     */
    void revokeDeviceTokens(Long userId, String deviceId);

    /** 设备是否处于被踢状态（未重新登录前）。 */
    boolean isDeviceKicked(Long userId, String deviceId);

    /** 登录成功后清除踢下线标记。 */
    void clearDeviceKick(Long userId, String deviceId);

    void assertAccessTokenActive(String accessToken);

    /** 校验 access token，并在提供 deviceId 时拒绝已踢设备。 */
    void assertAccessTokenActive(String accessToken, String deviceId);
}
