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
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.AppDownloadUrlResolver;
import com.linkx.server.service.AppVersionService;
import com.linkx.server.util.AppVersionUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
            return Result.success(buildFromPublished(published.get(), currentVersion, hasCurrent, clientPlatform, channel));
        }
        return Result.success(buildFromRuntimeConfig(currentVersion, hasCurrent, channel, clientPlatform));
    }

    /**
     * 官网 / 外链下载最新安装包：从管理端版本发布 + MinIO（releases/）解析，302 至同源媒体代理或外链。
     */
    @Operation(summary = "下载最新安装包")
    @GetMapping("/installer")
    public org.springframework.http.ResponseEntity<Void> downloadInstaller(
            HttpServletRequest request,
            @RequestParam(value = "platform", defaultValue = "windows") String platform,
            @RequestParam(value = "channel", required = false) String channel) {
        String clientPlatform = AppVersionUtils.normalizePlatform(platform);
        Optional<SysAppVersion> published = appVersionService.findLatestPublished(clientPlatform, channel);

        String downloadKey = "";
        String fileName = "LinkX-Installer.exe";
        if (published.isPresent()) {
            SysAppVersion row = published.get();
            downloadKey = nullToEmpty(row.getDownloadUrl());
            if (StringUtils.hasText(row.getPackageFileName())) {
                fileName = row.getPackageFileName().trim();
            }
        }
        if (!StringUtils.hasText(downloadKey)) {
            downloadKey = nullToEmpty(linkxProperties.getApp().getDownloadUrl());
        }
        if (!StringUtils.hasText(downloadKey)) {
            throw new CustomException(404, "暂无可用安装包");
        }

        String resolved = appDownloadUrlResolver.resolveForClient(downloadKey);
        if (!StringUtils.hasText(resolved)) {
            throw new CustomException(404, "安装包地址无效");
        }

        URI location = toAbsoluteDownloadUri(request, resolved);
        return org.springframework.http.ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .location(location)
                .build();
    }

    private URI toAbsoluteDownloadUri(HttpServletRequest request, String resolved) {
        if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
            return URI.create(resolved);
        }
        String path = resolved;
        String query = null;
        int q = resolved.indexOf('?');
        if (q >= 0) {
            path = resolved.substring(0, q);
            query = resolved.substring(q + 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return ServletUriComponentsBuilder.fromRequest(request)
                .replacePath(request.getContextPath() + path)
                .replaceQuery(query)
                .build(true)
                .toUri();
    }

    private AppVersionVO buildFromPublished(SysAppVersion row,
                                            String currentVersion,
                                            boolean hasCurrent,
                                            String clientPlatform,
                                            String channel) {
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
                .currentReleaseNotes(resolveCurrentReleaseNotes(currentVersion, hasCurrent, clientPlatform, channel))
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
                .currentReleaseNotes(resolveCurrentReleaseNotes(currentVersion, hasCurrent, clientPlatform, channel))
                .downloadUrl(appDownloadUrlResolver.resolveForClient(nullToEmpty(app.getDownloadUrl())))
                .packageSha256("")
                .packageFileName("")
                .supportEmail(nullToEmpty(app.getSupportEmail()))
                .supportPhone(nullToEmpty(app.getSupportPhone()))
                .build();
    }

    private String resolveCurrentReleaseNotes(String currentVersion,
                                              boolean hasCurrent,
                                              String clientPlatform,
                                              String channel) {
        if (!hasCurrent) {
            return "";
        }
        Optional<SysAppVersion> published = appVersionService.findPublishedByVersion(
                clientPlatform, channel, currentVersion);
        if (published.isPresent()) {
            return nullToEmpty(published.get().getReleaseNotes());
        }
        LinkxProperties.App app = linkxProperties.getApp();
        String latest = nullToEmpty(app.getVersion());
        if (currentVersion.equals(latest)) {
            return nullToEmpty(app.getReleaseNotes());
        }
        return "";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
