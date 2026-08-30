package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.admin.SysAppVersion;

import java.util.List;
import java.util.Optional;

/**
 * 客户端版本检查：从已发布记录中解析当前平台/渠道下的最新版本。
 */
public interface AppVersionService {

    /**
     * 查找指定平台下、渠道可见的最高已发布版本（默认安装包格式）。
     */
    Optional<SysAppVersion> findLatestPublished(String platform, String clientChannel);

    /**
     * 查找指定平台/渠道/安装包格式下的最高已发布版本。
     */
    Optional<SysAppVersion> findLatestPublished(String platform, String clientChannel, String packageFormat);

    /**
     * 查找与客户端当前版本号完全匹配的已发布记录（用于「本次更新」弹窗）。
     */
    Optional<SysAppVersion> findPublishedByVersion(String platform, String clientChannel, String version);

    /**
     * 查找指定版本下所有已发布安装包（同平台多渠道可见范围内）。
     */
    List<SysAppVersion> findPublishedPackages(String platform, String clientChannel, String version);
}
