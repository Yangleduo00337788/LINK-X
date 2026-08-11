package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
/**
 * 本地存储等需经 API 中转的媒体：签发短时 HMAC 代理 URL。
 */
public interface StoredMediaProxyService {

    /** 相对路径（含 context-path=/api 时由前端拼接 API 根） */
    String wrapObjectKey(String objectKey, int expirySeconds);

    /** 校验签名并返回 object key */
    String verifyAndExtractKey(String objectKey, long expiresEpochSec, String signature);
}
