package com.linkx.server.service;

import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;

public interface TokenService {

    TokenVO issueTokenPair(SysUser user);

    TokenVO refreshAccessToken(String refreshToken);

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

    void assertAccessTokenActive(String accessToken);
}
