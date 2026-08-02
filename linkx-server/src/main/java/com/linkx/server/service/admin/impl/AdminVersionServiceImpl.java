package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminVersionDTO;
import com.linkx.server.controller.admin.dto.AdminVersionQueryDTO;
import com.linkx.server.controller.admin.vo.AdminVersionVO;
import com.linkx.server.entity.admin.SysAppVersion;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysAppVersionMapper;
import com.linkx.server.service.admin.AdminSettingService;
import com.linkx.server.service.admin.AdminVersionService;
import com.linkx.server.util.AppVersionUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminVersionServiceImpl implements AdminVersionService {

    private static final Set<String> STATUSES = Set.of(
            SysAppVersion.STATUS_DRAFT,
            SysAppVersion.STATUS_PUBLISHED,
            SysAppVersion.STATUS_ARCHIVED
    );

    private final SysAppVersionMapper versionMapper;
    private final AdminSettingService adminSettingService;

    @Override
    public PageResultVO<AdminVersionVO> list(AdminVersionQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysAppVersion::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysAppVersion::getVersion).like(kw)
                        .or(SysAppVersion::getReleaseNotes).like(kw)
                        .or(SysAppVersion::getDownloadUrl).like(kw);
            });
        }
        if (StringUtils.hasText(query.getVersionStatus())) {
            String status = query.getVersionStatus().trim();
            if (STATUSES.contains(status)) {
                qw.and(SysAppVersion::getStatus).eq(status);
            }
        }
        if (StringUtils.hasText(query.getChannel())) {
            String channel = AppVersionUtils.normalizeChannel(query.getChannel().trim());
            qw.and(SysAppVersion::getChannel).eq(channel);
        }
        if (query.getStartTime() != null) {
            qw.and(SysAppVersion::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysAppVersion::getCreateTime).le(new Date(query.getEndTime()));
        }
        long total = versionMapper.selectCountByQuery(qw);
        qw.orderBy(SysAppVersion::getCreateTime, false);
        qw.limit((page - 1L) * size, size);
        List<AdminVersionVO> items = versionMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminVersionVO detail(Long id) {
        return toVO(requireVersion(id));
    }

    @Override
    @Transactional
    public AdminVersionVO create(AdminVersionDTO dto, Long operatorId) {
        validateDto(dto);
        Date now = new Date();
        SysAppVersion entity = SysAppVersion.builder()
                .version(normalizeVersion(dto.getVersion()))
                .channel(AppVersionUtils.normalizeChannel(dto.getChannel()))
                .releaseNotes(nullToEmpty(dto.getReleaseNotes()))
                .downloadUrl(nullToEmpty(dto.getDownloadUrl()))
                .forceUpdate(Boolean.TRUE.equals(dto.getForceUpdate()))
                .minSupportedVersion(nullToEmpty(dto.getMinSupportedVersion()))
                .status(SysAppVersion.STATUS_DRAFT)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        versionMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminVersionVO update(Long id, AdminVersionDTO dto, Long operatorId) {
        validateDto(dto);
        SysAppVersion row = requireVersion(id);
        if (!SysAppVersion.STATUS_DRAFT.equals(row.getStatus())) {
            throw new CustomException(400, "仅草稿版本可编辑");
        }
        row.setVersion(normalizeVersion(dto.getVersion()));
        row.setChannel(AppVersionUtils.normalizeChannel(dto.getChannel()));
        row.setReleaseNotes(nullToEmpty(dto.getReleaseNotes()));
        row.setDownloadUrl(nullToEmpty(dto.getDownloadUrl()));
        row.setForceUpdate(Boolean.TRUE.equals(dto.getForceUpdate()));
        row.setMinSupportedVersion(nullToEmpty(dto.getMinSupportedVersion()));
        row.setUpdatedBy(operatorId);
        row.setUpdateTime(new Date());
        versionMapper.update(row);
        return toVO(row);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysAppVersion row = requireVersion(id);
        if (!SysAppVersion.STATUS_DRAFT.equals(row.getStatus())) {
            throw new CustomException(400, "仅草稿版本可删除");
        }
        row.setDeleted(1);
        row.setUpdatedBy(operatorId);
        row.setUpdateTime(new Date());
        versionMapper.update(row);
    }

    @Override
    @Transactional
    public AdminVersionVO publish(Long id, Long operatorId) {
        SysAppVersion row = requireVersion(id);
        if (SysAppVersion.STATUS_PUBLISHED.equals(row.getStatus())) {
            throw new CustomException(400, "版本已发布");
        }
        if (!SysAppVersion.STATUS_DRAFT.equals(row.getStatus())) {
            throw new CustomException(400, "仅草稿版本可发布");
        }
        Date now = new Date();
        List<SysAppVersion> published = versionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAppVersion::getDeleted).eq(0)
                        .and(SysAppVersion::getStatus).eq(SysAppVersion.STATUS_PUBLISHED));
        for (SysAppVersion prev : published) {
            prev.setStatus(SysAppVersion.STATUS_ARCHIVED);
            prev.setUpdateTime(now);
            prev.setUpdatedBy(operatorId);
            versionMapper.update(prev);
        }
        row.setStatus(SysAppVersion.STATUS_PUBLISHED);
        row.setPublishedAt(now);
        row.setPublishedBy(operatorId);
        row.setUpdatedBy(operatorId);
        row.setUpdateTime(now);
        versionMapper.update(row);
        adminSettingService.syncPublishedAppVersion(
                row.getVersion(),
                row.getChannel(),
                row.getReleaseNotes(),
                row.getDownloadUrl(),
                row.getForceUpdate(),
                row.getMinSupportedVersion(),
                operatorId);
        return toVO(row);
    }

    private SysAppVersion requireVersion(Long id) {
        SysAppVersion row = versionMapper.selectOneById(id);
        if (row == null || Integer.valueOf(1).equals(row.getDeleted())) {
            throw new CustomException(404, "版本不存在");
        }
        return row;
    }

    private void validateDto(AdminVersionDTO dto) {
        String version = normalizeVersion(dto.getVersion());
        if (!version.matches("\\d+(\\.\\d+)*")) {
            throw new CustomException(400, "版本号格式无效，示例：1.0.0");
        }
        String minSupported = nullToEmpty(dto.getMinSupportedVersion());
        if (StringUtils.hasText(minSupported) && AppVersionUtils.compare(minSupported, version) > 0) {
            throw new CustomException(400, "最低支持版本不能高于应用版本");
        }
    }

    private String normalizeVersion(String version) {
        if (!StringUtils.hasText(version)) {
            throw new CustomException(400, "版本号不能为空");
        }
        return version.trim();
    }

    private AdminVersionVO toVO(SysAppVersion row) {
        return AdminVersionVO.builder()
                .id(row.getId())
                .version(row.getVersion())
                .channel(row.getChannel())
                .releaseNotes(row.getReleaseNotes())
                .downloadUrl(row.getDownloadUrl())
                .forceUpdate(row.getForceUpdate())
                .minSupportedVersion(row.getMinSupportedVersion())
                .status(row.getStatus())
                .publishedAt(row.getPublishedAt())
                .publishedBy(row.getPublishedBy())
                .createdBy(row.getCreatedBy())
                .updatedBy(row.getUpdatedBy())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
