package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import java.time.Duration;

/**
 * API 签名 nonce 防重放（Redis SET NX）。
 */
public interface ApiSignNonceService {

    /**
     * 登记 nonce；若已存在则返回 false。
     */
    boolean registerNonce(String nonce, Duration ttl);
}
