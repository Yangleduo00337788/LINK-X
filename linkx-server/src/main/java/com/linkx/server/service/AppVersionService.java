package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.admin.SysAppVersion;

import java.util.Optional;

/**
 * 客户端版本检查：从已发布记录中解析当前平台/渠道下的最新版本。
 */
public interface AppVersionService {

    /**
     * 查找指定平台下、渠道可见的最高已发布版本。
     */
    Optional<SysAppVersion> findLatestPublished(String platform, String clientChannel);
}
