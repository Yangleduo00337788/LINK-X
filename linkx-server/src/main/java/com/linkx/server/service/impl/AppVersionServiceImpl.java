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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppVersionServiceImpl implements AppVersionService {

    private final SysAppVersionMapper versionMapper;

    @Override
    public Optional<SysAppVersion> findLatestPublished(String platform, String clientChannel) {
        String normalizedPlatform = AppVersionUtils.normalizePlatform(platform);
        List<SysAppVersion> published = versionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAppVersion::getDeleted).eq(0)
                        .and(SysAppVersion::getStatus).eq(SysAppVersion.STATUS_PUBLISHED)
                        .and(SysAppVersion::getPlatform).eq(normalizedPlatform));
        return published.stream()
                .filter(row -> AppVersionUtils.isChannelEligible(clientChannel, row.getChannel()))
                .max(Comparator.comparing(SysAppVersion::getVersion, AppVersionUtils::compare));
    }
}
