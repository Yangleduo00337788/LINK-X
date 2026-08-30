package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.admin.SysAppVersion;
import com.linkx.server.mapper.admin.SysAppVersionMapper;
import com.linkx.server.service.AppVersionService;
import com.linkx.server.util.AppVersionUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppVersionServiceImpl implements AppVersionService {

    private final SysAppVersionMapper versionMapper;

    @Override
    public Optional<SysAppVersion> findLatestPublished(String platform, String clientChannel) {
        return findLatestPublished(platform, clientChannel, null);
    }

    @Override
    public Optional<SysAppVersion> findLatestPublished(String platform, String clientChannel, String packageFormat) {
        List<SysAppVersion> published = listPublished(platform);
        Optional<String> latestVersion = published.stream()
                .filter(row -> AppVersionUtils.isChannelEligible(clientChannel, row.getChannel()))
                .map(SysAppVersion::getVersion)
                .max(AppVersionUtils::compare);
        if (latestVersion.isEmpty()) {
            return Optional.empty();
        }
        String format = StringUtils.hasText(packageFormat)
                ? AppVersionUtils.normalizePackageFormat(packageFormat)
                : AppVersionUtils.defaultPackageFormat(platform);
        return published.stream()
                .filter(row -> AppVersionUtils.isChannelEligible(clientChannel, row.getChannel()))
                .filter(row -> latestVersion.get().equals(row.getVersion()))
                .filter(row -> format.equals(AppVersionUtils.normalizePackageFormat(row.getPackageFormat())))
                .max(Comparator.comparing(SysAppVersion::getPublishedAt, Comparator.nullsFirst(Date::compareTo)))
                .or(() -> published.stream()
                        .filter(row -> AppVersionUtils.isChannelEligible(clientChannel, row.getChannel()))
                        .filter(row -> latestVersion.get().equals(row.getVersion()))
                        .max(Comparator.comparing(SysAppVersion::getPublishedAt, Comparator.nullsFirst(Date::compareTo))));
    }

    @Override
    public Optional<SysAppVersion> findPublishedByVersion(String platform, String clientChannel, String version) {
        if (!StringUtils.hasText(version)) {
            return Optional.empty();
        }
        String normalizedPlatform = AppVersionUtils.normalizePlatform(platform);
        String normalizedVersion = version.trim();
        List<SysAppVersion> published = versionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAppVersion::getDeleted).eq(0)
                        .and(SysAppVersion::getStatus).eq(SysAppVersion.STATUS_PUBLISHED)
                        .and(SysAppVersion::getPlatform).eq(normalizedPlatform)
                        .and(SysAppVersion::getVersion).eq(normalizedVersion));
        return published.stream()
                .filter(row -> AppVersionUtils.isChannelEligible(clientChannel, row.getChannel()))
                .max(Comparator
                        .comparing((SysAppVersion row) -> AppVersionUtils.defaultPackageFormat(platform)
                                .equals(AppVersionUtils.normalizePackageFormat(row.getPackageFormat())))
                        .thenComparing(SysAppVersion::getPublishedAt, Comparator.nullsFirst(Date::compareTo)));
    }

    @Override
    public List<SysAppVersion> findPublishedPackages(String platform, String clientChannel, String version) {
        if (!StringUtils.hasText(version)) {
            return List.of();
        }
        String normalizedPlatform = AppVersionUtils.normalizePlatform(platform);
        String normalizedVersion = version.trim();
        return versionMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(SysAppVersion::getDeleted).eq(0)
                                .and(SysAppVersion::getStatus).eq(SysAppVersion.STATUS_PUBLISHED)
                                .and(SysAppVersion::getPlatform).eq(normalizedPlatform)
                                .and(SysAppVersion::getVersion).eq(normalizedVersion))
                .stream()
                .filter(row -> AppVersionUtils.isChannelEligible(clientChannel, row.getChannel()))
                .sorted(Comparator
                        .comparing((SysAppVersion row) -> AppVersionUtils.normalizePackageFormat(row.getPackageFormat()))
                        .thenComparing(SysAppVersion::getPublishedAt, Comparator.nullsFirst(Date::compareTo)))
                .toList();
    }

    private List<SysAppVersion> listPublished(String platform) {
        String normalizedPlatform = AppVersionUtils.normalizePlatform(platform);
        return versionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAppVersion::getDeleted).eq(0)
                        .and(SysAppVersion::getStatus).eq(SysAppVersion.STATUS_PUBLISHED)
                        .and(SysAppVersion::getPlatform).eq(normalizedPlatform));
    }
}
