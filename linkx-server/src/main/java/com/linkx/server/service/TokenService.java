package com.linkx.server.service;

import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;

public interface TokenService {

    TokenVO issueTokenPair(SysUser user);

    TokenVO refreshAccessToken(String refreshToken);

    void logout(String authorization, String refreshToken);

    void assertAccessTokenActive(String accessToken);
}
