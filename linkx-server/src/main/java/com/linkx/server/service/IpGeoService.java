package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
/**
 * IP 归属地解析（离线优先）。
 */
public interface IpGeoService {

    /**
     * @return 可读归属地文案；空 IP 返回 "-"；内网返回「内网」；无库时公网返回「未知」
     */
    String resolve(String ip);
}
