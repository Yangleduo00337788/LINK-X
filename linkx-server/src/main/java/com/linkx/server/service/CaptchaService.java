package com.linkx.server.service;

import com.linkx.server.controller.vo.CaptchaVO;

public interface CaptchaService {

    CaptchaVO generate();

    /**
     * 生成绑定到指定账号的验证码。
     * @param ownerId 账号标识（实际传 userId 字符串，防止误传真实 username）
     * @return 验证码 VO
     */
    CaptchaVO generateForOwner(String ownerId);

    void validate(String captchaId, String captchaCode);

    /**
     * 验证账号绑定的验证码（密码重置专用）
     * @param ownerId 账号标识（与 generateForOwner 传入值一致）
     * @param captchaId 验证码ID
     * @param captchaCode 验证码
     */
    void validateForOwner(String ownerId, String captchaId, String captchaCode);

    /**
     * @deprecated Use {@link #generateForOwner(String)} and {@link #validateForOwner(String, String, String)}
     */
    @Deprecated
    default CaptchaVO generateForUser(String username) { return generateForOwner(username); }

    /**
     * @deprecated Use {@link #validateForOwner(String, String, String)}
     */
    @Deprecated
    default void validateForUser(String username, String captchaId, String captchaCode) {
        validateForOwner(username, captchaId, captchaCode);
    }

    /**
     * 检查验证码功能是否启用
     */
    boolean isEnabled();
}
