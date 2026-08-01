package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.AppVersionVO;
import com.linkx.server.util.AppVersionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用版本控制器：用于"检查更新"。
 * <p>
 * 路由：GET /app/version?current=1.0.0&amp;channel=stable
 *  - 若客户端未传 current，则认为"未知"，永远返回 hasUpdate=false（避免误导）。
 *  - 版本比较采用"按 '.' 拆段 + 数字逐段比较"；位数不足视为 0。
 *  - channel：灰度可见性；未传则兼容旧客户端（一律可见）。
 *  - forceUpdate：管理端开关，或当前版本低于 minSupportedVersion。
 * </p>
 */
@RestController
@Tag(name = "${openapi.tag.version}")
@RequestMapping("/app")
public class VersionController {

    private final LinkxProperties linkxProperties;

    public VersionController(LinkxProperties linkxProperties) {
        this.linkxProperties = linkxProperties;
    }

    @GetMapping("/version")
    public Result<AppVersionVO> checkVersion(
            @RequestParam(value = "current", required = false) String current,
            @RequestParam(value = "channel", required = false) String channel) {
        LinkxProperties.App app = linkxProperties.getApp();
        String latest = nullToEmpty(app.getVersion());
        String currentVersion = current == null ? "" : current.trim();
        boolean hasCurrent = StringUtils.hasText(currentVersion);
        boolean versionOutdated = hasCurrent && AppVersionUtils.compare(currentVersion, latest) < 0;
        boolean channelEligible = AppVersionUtils.isChannelEligible(channel, app.getChannel());

        String minSupported = nullToEmpty(app.getMinSupportedVersion());
        boolean belowMin = hasCurrent && StringUtils.hasText(minSupported)
                && AppVersionUtils.compare(currentVersion, minSupported) < 0;

        // 低于最低支持版本时忽略灰度，强制提示升级
        boolean hasUpdate = belowMin || (channelEligible && versionOutdated);
        boolean forceUpdate = belowMin || (hasUpdate && Boolean.TRUE.equals(app.getForceUpdate()));

        return Result.success(AppVersionVO.builder()
                .version(latest)
                .currentVersion(currentVersion)
                .hasUpdate(hasUpdate)
                .forceUpdate(forceUpdate)
                .channel(AppVersionUtils.normalizeChannel(app.getChannel()))
                .releaseNotes(hasUpdate ? nullToEmpty(app.getReleaseNotes()) : "当前已是最新版本")
                .downloadUrl(nullToEmpty(app.getDownloadUrl()))
                .build());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
