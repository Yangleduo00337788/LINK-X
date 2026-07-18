package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 应用版本信息 VO（GET /app/version 响应体）
 * <p>
 * 字段语义：
 * - version：服务端最新版本；
 * - currentVersion：客户端当前版本（来自请求参数）；
 * - hasUpdate：true 表示需要提示升级；
 * - forceUpdate：true 表示强制升级（保留字段，预留策略）；
 * - releaseNotes：升级提示；
 * - downloadUrl：下载地址（可空）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;
    private String currentVersion;
    private boolean hasUpdate;
    private boolean forceUpdate;
    private String channel;
    private String releaseNotes;
    private String downloadUrl;
}