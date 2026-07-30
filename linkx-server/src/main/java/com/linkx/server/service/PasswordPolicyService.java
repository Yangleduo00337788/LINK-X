package com.linkx.server.service;

/**
 * 统一密码策略校验（注册 / 改密 / 重置；管理端与客户端共用）。
 */
public interface PasswordPolicyService {

    /**
     * 校验明文密码是否符合当前运行时策略；不通过时抛出业务异常。
     */
    void validate(String password);
}
