package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.AppVersionVO;
import com.linkx.server.entity.admin.SysAppVersion;
import com.linkx.server.service.AppDownloadUrlResolver;
import com.linkx.server.service.AppVersionService;
import com.linkx.server.util.AppVersionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 应用版本控制器：用于"检查更新"。
 * <p>
 * 路由：GET /app/version?current=1.0.0&amp;channel=stable&amp;platform=windows
 *  - 优先从已发布版本记录（按平台 + 渠道）解析最新版本；
 *  - 若无记录则回退到运行时配置 linkx.app.*。
 * </p>
 */
@RestController
@Tag(name = "${openapi.tag.version}")
@RequestMapping("/app")
public class VersionController {

    private final LinkxProperties linkxProperties;
    private final AppDownloadUrlResolver appDownloadUrlResolver;
    private final AppVersionService appVersionService;

    public VersionController(LinkxProperties linkxProperties,
                             AppDownloadUrlResolver appDownloadUrlResolver,
                             AppVersionService appVersionService) {
        this.linkxProperties = linkxProperties;
        this.appDownloadUrlResolver = appDownloadUrlResolver;
        this.appVersionService = appVersionService;
    }

    @Operation(summary = "检查应用更新")
    @GetMapping("/version")
    public Result<AppVersionVO> checkVersion(
            @RequestParam(value = "current", required = false) String current,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "platform", required = false) String platform) {
        String currentVersion = current == null ? "" : current.trim();
        boolean hasCurrent = StringUtils.hasText(currentVersion);
        String clientPlatform = AppVersionUtils.normalizePlatform(platform);

        Optional<SysAppVersion> published = appVersionService.findLatestPublished(clientPlatform, channel);
        if (published.isPresent()) {
            return Result.success(buildFromPublished(published.get(), currentVersion, hasCurrent, clientPlatform));
        }
        return Result.success(buildFromRuntimeConfig(currentVersion, hasCurrent, channel, clientPlatform));
    }

    private AppVersionVO buildFromPublished(SysAppVersion row,
                                            String currentVersion,
                                            boolean hasCurrent,
                                            String clientPlatform) {
        String latest = nullToEmpty(row.getVersion());
        boolean versionOutdated = hasCurrent && AppVersionUtils.compare(currentVersion, latest) < 0;
        String minSupported = nullToEmpty(row.getMinSupportedVersion());
        boolean belowMin = hasCurrent && StringUtils.hasText(minSupported)
                && AppVersionUtils.compare(currentVersion, minSupported) < 0;
        boolean hasUpdate = belowMin || versionOutdated;
        boolean forceUpdate = belowMin || (hasUpdate && Boolean.TRUE.equals(row.getForceUpdate()));

        LinkxProperties.App app = linkxProperties.getApp();
        return AppVersionVO.builder()
                .version(latest)
                .currentVersion(currentVersion)
                .hasUpdate(hasUpdate)
                .forceUpdate(forceUpdate)
                .channel(AppVersionUtils.normalizeChannel(row.getChannel()))
                .platform(clientPlatform)
                .releaseNotes(hasUpdate ? nullToEmpty(row.getReleaseNotes()) : "当前已是最新版本")
                .downloadUrl(hasUpdate
                        ? appDownloadUrlResolver.resolveForClient(nullToEmpty(row.getDownloadUrl()))
                        : "")
                .packageSha256(hasUpdate ? nullToEmpty(row.getPackageSha256()) : "")
                .packageFileName(hasUpdate ? nullToEmpty(row.getPackageFileName()) : "")
                .supportEmail(nullToEmpty(app.getSupportEmail()))
                .supportPhone(nullToEmpty(app.getSupportPhone()))
                .build();
    }

    private AppVersionVO buildFromRuntimeConfig(String currentVersion,
                                                boolean hasCurrent,
                                                String channel,
                                                String clientPlatform) {
        LinkxProperties.App app = linkxProperties.getApp();
        String latest = nullToEmpty(app.getVersion());
        boolean versionOutdated = hasCurrent && AppVersionUtils.compare(currentVersion, latest) < 0;
        boolean channelEligible = AppVersionUtils.isChannelEligible(channel, app.getChannel());
        String minSupported = nullToEmpty(app.getMinSupportedVersion());
        boolean belowMin = hasCurrent && StringUtils.hasText(minSupported)
                && AppVersionUtils.compare(currentVersion, minSupported) < 0;
        boolean hasUpdate = belowMin || (channelEligible && versionOutdated);
        boolean forceUpdate = belowMin || (hasUpdate && Boolean.TRUE.equals(app.getForceUpdate()));

        return AppVersionVO.builder()
                .version(latest)
                .currentVersion(currentVersion)
                .hasUpdate(hasUpdate)
                .forceUpdate(forceUpdate)
                .channel(AppVersionUtils.normalizeChannel(app.getChannel()))
                .platform(clientPlatform)
                .releaseNotes(hasUpdate ? nullToEmpty(app.getReleaseNotes()) : "当前已是最新版本")
                .downloadUrl(appDownloadUrlResolver.resolveForClient(nullToEmpty(app.getDownloadUrl())))
                .packageSha256("")
                .packageFileName("")
                .supportEmail(nullToEmpty(app.getSupportEmail()))
                .supportPhone(nullToEmpty(app.getSupportPhone()))
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
