package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
/**
 * 朋友圈等外链图片：签发短时 HMAC 代理 URL，并由服务端代拉（防盗链/降追踪面）。
 */
public interface ExternalMediaProxyService {

    /** 将外链包装为 /media/external?... 相对路径（相对 context-path=/api） */
    String wrapExternalUrl(String absoluteHttpUrl);

    /** 校验签名并代拉图片流 */
    ProxiedImage fetch(String url, long expiresEpochSec, String signature);

    record ProxiedImage(byte[] body, String contentType) {
    }
}
