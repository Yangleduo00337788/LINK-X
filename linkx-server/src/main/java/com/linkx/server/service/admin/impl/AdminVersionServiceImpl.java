package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.AdminKeywordQuery;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminVersionDTO;
import com.linkx.server.controller.admin.dto.AdminVersionQueryDTO;
import com.linkx.server.controller.admin.dto.AdminVersionDirectMultipartCompleteDTO;
import com.linkx.server.controller.admin.dto.AdminVersionDirectPresignPartsDTO;
import com.linkx.server.controller.admin.dto.AdminVersionMultipartCompleteDTO;
import com.linkx.server.controller.admin.dto.AdminVersionMultipartInitDTO;
import com.linkx.server.controller.admin.vo.AdminVersionDirectMultipartInitVO;
import com.linkx.server.controller.admin.vo.AdminVersionDirectPresignPartsVO;
import com.linkx.server.controller.admin.vo.AdminVersionMultipartInitVO;
import com.linkx.server.controller.admin.vo.AdminVersionUploadCapabilityVO;
import com.linkx.server.controller.admin.vo.AdminVersionUploadVO;
import com.linkx.server.controller.admin.vo.AdminVersionVO;
import com.linkx.server.entity.admin.SysAppVersion;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysAppVersionMapper;
import com.linkx.server.service.AppDownloadUrlResolver;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.AdminSettingService;
import com.linkx.server.service.admin.AdminVersionService;
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.storage.S3NativeMultipartSupport;
import com.linkx.server.util.AppVersionUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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
    private final FileStorageService fileStorageService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;
    private final AppDownloadUrlResolver appDownloadUrlResolver;
    private final ObjectStorageRouter objectStorageRouter;

    @Override
    public PageResultVO<AdminVersionVO> list(AdminVersionQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysAppVersion::getDeleted).eq(0);
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
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
        if (StringUtils.hasText(query.getPlatform())) {
            String platform = AppVersionUtils.normalizePlatform(query.getPlatform().trim());
            qw.and(SysAppVersion::getPlatform).eq(platform);
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
                .platform(AppVersionUtils.normalizePlatform(dto.getPlatform()))
                .releaseNotes(nullToEmpty(dto.getReleaseNotes()))
                .downloadUrl(nullToEmpty(dto.getDownloadUrl()))
                .packageSha256(nullToEmpty(dto.getPackageSha256()))
                .packageFileName(nullToEmpty(dto.getPackageFileName()))
                .packageSize(dto.getPackageSize())
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
        row.setPlatform(AppVersionUtils.normalizePlatform(dto.getPlatform()));
        row.setReleaseNotes(nullToEmpty(dto.getReleaseNotes()));
        row.setDownloadUrl(nullToEmpty(dto.getDownloadUrl()));
        row.setPackageSha256(nullToEmpty(dto.getPackageSha256()));
        row.setPackageFileName(nullToEmpty(dto.getPackageFileName()));
        row.setPackageSize(dto.getPackageSize());
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
        String platform = AppVersionUtils.normalizePlatform(row.getPlatform());
        String channel = AppVersionUtils.normalizeChannel(row.getChannel());
        List<SysAppVersion> published = versionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAppVersion::getDeleted).eq(0)
                        .and(SysAppVersion::getStatus).eq(SysAppVersion.STATUS_PUBLISHED)
                        .and(SysAppVersion::getPlatform).eq(platform)
                        .and(SysAppVersion::getChannel).eq(channel));
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

    @Override
    public AdminVersionUploadVO uploadPackage(MultipartFile file, Long operatorId) {
        FileStorageService.InstallerUploadResult uploaded = fileStorageService.uploadInstaller(file);
        if (operatorId != null) {
            objectKeyOwnershipService.claim(operatorId, uploaded.objectKey());
        }
        return AdminVersionUploadVO.builder()
                .objectKey(uploaded.objectKey())
                .url(appDownloadUrlResolver.resolveForAdmin(uploaded.objectKey()))
                .sha256(uploaded.sha256())
                .fileName(uploaded.fileName())
                .fileSize(uploaded.size())
                .build();
    }

    @Override
    public AdminVersionMultipartInitVO initInstallerMultipart(AdminVersionMultipartInitDTO dto, Long operatorId) {
        FileStorageService.InstallerMultipartSession session =
                fileStorageService.initiateInstallerMultipart(dto.getFileName());
        if (operatorId != null) {
            objectKeyOwnershipService.claim(operatorId, session.objectKey());
        }
        return AdminVersionMultipartInitVO.builder()
                .uploadId(session.uploadId())
                .objectKey(session.objectKey())
                .build();
    }

    @Override
    public void uploadInstallerPart(MultipartFile file, String uploadId, String objectKey, int partNumber, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "分片不能为空");
        }
        if (partNumber < 1) {
            throw new CustomException(400, "分片序号无效");
        }
        long maxChunk = 16L * 1024 * 1024;
        if (file.getSize() > maxChunk) {
            throw new CustomException(400, "单片不能超过 16MB");
        }
        try {
            fileStorageService.uploadPart(objectKey, uploadId, partNumber, file.getInputStream(), file.getSize());
        } catch (CustomException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (Exception e) {
            throw new CustomException(500, "分片上传失败");
        }
    }

    @Override
    public AdminVersionUploadVO completeInstallerMultipart(AdminVersionMultipartCompleteDTO dto, Long operatorId) {
        FileStorageService.InstallerUploadResult uploaded = fileStorageService.completeInstallerMultipart(
                dto.getObjectKey(),
                dto.getUploadId(),
                dto.getFileName(),
                dto.getFileSize(),
                dto.getPackageSha256());
        if (operatorId != null) {
            objectKeyOwnershipService.claim(operatorId, uploaded.objectKey());
        }
        return AdminVersionUploadVO.builder()
                .objectKey(uploaded.objectKey())
                .url(appDownloadUrlResolver.resolveForAdmin(uploaded.objectKey()))
                .sha256(uploaded.sha256())
                .fileName(uploaded.fileName())
                .fileSize(uploaded.size())
                .build();
    }

    @Override
    public AdminVersionUploadCapabilityVO uploadCapability() {
        return AdminVersionUploadCapabilityVO.builder()
                .directMultipart(objectStorageRouter.supportsDirectMultipartUpload())
                .provider(objectStorageRouter.activeProvider().toWire())
                .chunkSize(S3NativeMultipartSupport.INSTALLER_CHUNK_BYTES)
                .maxConcurrency(S3NativeMultipartSupport.INSTALLER_UPLOAD_MAX_CONCURRENCY)
                .build();
    }

    @Override
    public AdminVersionDirectMultipartInitVO initInstallerDirectMultipart(
            AdminVersionMultipartInitDTO dto, Long operatorId) {
        FileStorageService.InstallerMultipartSession session =
                fileStorageService.initiateInstallerDirectMultipart(dto.getFileName());
        if (operatorId != null) {
            objectKeyOwnershipService.claim(operatorId, session.objectKey());
        }
        return AdminVersionDirectMultipartInitVO.builder()
                .uploadId(session.uploadId())
                .objectKey(session.objectKey())
                .chunkSize(S3NativeMultipartSupport.INSTALLER_CHUNK_BYTES)
                .maxConcurrency(S3NativeMultipartSupport.INSTALLER_UPLOAD_MAX_CONCURRENCY)
                .build();
    }

    @Override
    public AdminVersionDirectPresignPartsVO presignInstallerDirectParts(AdminVersionDirectPresignPartsDTO dto) {
        List<FileStorageService.DirectPartPresign> parts = fileStorageService.presignInstallerDirectParts(
                dto.getObjectKey(), dto.getUploadId(), dto.getTotalParts());
        return AdminVersionDirectPresignPartsVO.builder()
                .chunkSize(S3NativeMultipartSupport.INSTALLER_CHUNK_BYTES)
                .parts(parts.stream()
                        .map(p -> AdminVersionDirectPresignPartsVO.PartUrl.builder()
                                .partNumber(p.partNumber())
                                .url(p.url())
                                .build())
                        .toList())
                .build();
    }

    @Override
    public AdminVersionUploadVO completeInstallerDirectMultipart(
            AdminVersionDirectMultipartCompleteDTO dto, Long operatorId) {
        List<FileStorageService.PartETag> parts = dto.getParts().stream()
                .map(p -> new FileStorageService.PartETag(p.getPartNumber(), p.getEtag()))
                .toList();
        FileStorageService.InstallerUploadResult uploaded = fileStorageService.completeInstallerDirectMultipart(
                dto.getObjectKey(),
                dto.getUploadId(),
                dto.getFileName(),
                dto.getFileSize(),
                dto.getPackageSha256(),
                parts);
        if (operatorId != null) {
            objectKeyOwnershipService.claim(operatorId, uploaded.objectKey());
        }
        return AdminVersionUploadVO.builder()
                .objectKey(uploaded.objectKey())
                .url(appDownloadUrlResolver.resolveForAdmin(uploaded.objectKey()))
                .sha256(uploaded.sha256())
                .fileName(uploaded.fileName())
                .fileSize(uploaded.size())
                .build();
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
        if (!AppVersionUtils.isValidPlatform(dto.getPlatform())) {
            throw new CustomException(400, "目标平台无效，可选：windows / macos / linux");
        }
    }

    private String normalizeVersion(String version) {
        if (!StringUtils.hasText(version)) {
            throw new CustomException(400, "版本号不能为空");
        }
        return version.trim();
    }

    private AdminVersionVO toVO(SysAppVersion row) {
        String stored = row.getDownloadUrl();
        return AdminVersionVO.builder()
                .id(row.getId())
                .version(row.getVersion())
                .channel(row.getChannel())
                .platform(row.getPlatform())
                .releaseNotes(row.getReleaseNotes())
                .downloadKey(stored)
                .downloadUrl(appDownloadUrlResolver.resolveForAdmin(stored))
                .packageSha256(row.getPackageSha256())
                .packageFileName(row.getPackageFileName())
                .packageSize(row.getPackageSize())
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
