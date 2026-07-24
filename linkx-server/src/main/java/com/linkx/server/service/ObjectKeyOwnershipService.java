package com.linkx.server.service;

/**
 * MinIO object key 属主登记：上传时 claim，签发/挂载前 assertOwned，防止任意 key 越权访问。
 */
public interface ObjectKeyOwnershipService {

    /** 登记 objectKey 属于 userId（覆盖写入） */
    void claim(Long userId, String objectKey);

    /** 当前用户是否为该 key 的登记属主 */
    boolean isOwned(Long userId, String objectKey);

    /** 非属主则抛 403 */
    void assertOwned(Long userId, String objectKey);
}
