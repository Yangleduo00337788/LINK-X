package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ImageUploadValidator;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminActivityDTO;
import com.linkx.server.controller.admin.dto.AdminActivityQueryDTO;
import com.linkx.server.controller.admin.vo.AdminActivityUploadVO;
import com.linkx.server.controller.admin.vo.AdminActivityVO;
import com.linkx.server.controller.vo.AppActivityVO;
import com.linkx.server.entity.admin.SysOpsActivity;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysOpsActivityMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.AdminActivityService;
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
public class AdminActivityServiceImpl implements AdminActivityService {

    private static final Set<String> STATUSES = Set.of(
            SysOpsActivity.STATUS_DRAFT,
            SysOpsActivity.STATUS_PUBLISHED,
            SysOpsActivity.STATUS_UNPUBLISHED
    );

    private final SysOpsActivityMapper activityMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;

    @Override
    public PageResultVO<AdminActivityVO> list(AdminActivityQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysOpsActivity::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysOpsActivity::getTitle).like(kw)
                        .or(SysOpsActivity::getDescription).like(kw)
                        .or(SysOpsActivity::getCoverUrl).like(kw)
                        .or(SysOpsActivity::getLinkUrl).like(kw);
            });
        }
        if (StringUtils.hasText(query.getActivityStatus())) {
            String status = query.getActivityStatus().trim();
            if (STATUSES.contains(status)) {
                qw.and(SysOpsActivity::getStatus).eq(status);
            }
        }
        if (query.getStartTime() != null) {
            qw.and(SysOpsActivity::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysOpsActivity::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(SysOpsActivity::getSortOrder, true)
                .orderBy(SysOpsActivity::getUpdateTime, false);
        long total = activityMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminActivityVO> items = activityMapper.selectListByQuery(qw).stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminActivityVO detail(Long id) {
        return toAdminVO(requireActivity(id));
    }

    @Override
    @Transactional
    public AdminActivityVO create(AdminActivityDTO dto, Long operatorId) {
        Date now = new Date();
        Date startAt = toDate(dto.getStartAt());
        Date endAt = toDate(dto.getEndAt());
        validateWindow(startAt, endAt);
        SysOpsActivity entity = SysOpsActivity.builder()
                .title(normalizeOptionalText(dto.getTitle(), 128))
                .coverUrl(normalizeCoverRef(dto.getCoverUrl()))
                .linkUrl(normalizeOptionalHttpUrl(dto.getLinkUrl()))
                .description(normalizeOptionalText(dto.getDescription(), 1000))
                .sortOrder(normalizeSortOrder(dto.getSortOrder()))
                .status(SysOpsActivity.STATUS_DRAFT)
                .startAt(startAt)
                .endAt(endAt)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        activityMapper.insert(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public AdminActivityVO update(Long id, AdminActivityDTO dto, Long operatorId) {
        SysOpsActivity entity = requireActivity(id);
        if (SysOpsActivity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published activity cannot be edited, unpublish first");
        }
        Date startAt = toDate(dto.getStartAt());
        Date endAt = toDate(dto.getEndAt());
        validateWindow(startAt, endAt);
        entity.setTitle(normalizeOptionalText(dto.getTitle(), 128));
        entity.setCoverUrl(normalizeCoverRef(dto.getCoverUrl()));
        entity.setLinkUrl(normalizeOptionalHttpUrl(dto.getLinkUrl()));
        entity.setDescription(normalizeOptionalText(dto.getDescription(), 1000));
        entity.setSortOrder(normalizeSortOrder(dto.getSortOrder()));
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        activityMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysOpsActivity entity = requireActivity(id);
        if (SysOpsActivity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published activity cannot be deleted, unpublish first");
        }
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        activityMapper.update(entity);
    }

    @Override
    @Transactional
    public AdminActivityVO publish(Long id, Long operatorId) {
        SysOpsActivity entity = requireActivity(id);
        if (SysOpsActivity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "activity already published");
        }
        Date now = new Date();
        entity.setStatus(SysOpsActivity.STATUS_PUBLISHED);
        entity.setPublishedAt(now);
        entity.setPublishedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        activityMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public AdminActivityVO unpublish(Long id, Long operatorId) {
        SysOpsActivity entity = requireActivity(id);
        if (!SysOpsActivity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "only published activity can be unpublished");
        }
        Date now = new Date();
        entity.setStatus(SysOpsActivity.STATUS_UNPUBLISHED);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        activityMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    public AdminActivityUploadVO uploadImage(MultipartFile file, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "file required");
        }
        try {
            ImageUploadValidator.assertSupportedImage(file);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        }
        String objectKey = fileStorageService.uploadFile(file, null);
        if (operatorId != null) {
            objectKeyOwnershipService.claim(operatorId, objectKey);
        }
        String previewUrl = mediaUrlService.resolveAvatar(objectKey);
        return AdminActivityUploadVO.builder()
                .objectKey(objectKey)
                .url(previewUrl)
                .build();
    }

    @Override
    public List<AppActivityVO> listPublishedForClient() {
        Date now = new Date();
        QueryWrapper qw = QueryWrapper.create()
                .where(SysOpsActivity::getDeleted).eq(0)
                .and(SysOpsActivity::getStatus).eq(SysOpsActivity.STATUS_PUBLISHED);
        qw.and((QueryWrapper w) -> {
            w.where(SysOpsActivity::getStartAt).isNull()
                    .or(SysOpsActivity::getStartAt).le(now);
        });
        qw.and((QueryWrapper w) -> {
            w.where(SysOpsActivity::getEndAt).isNull()
                    .or(SysOpsActivity::getEndAt).ge(now);
        });
        qw.orderBy(SysOpsActivity::getSortOrder, true)
                .orderBy(SysOpsActivity::getId, true);
        return activityMapper.selectListByQuery(qw).stream()
                .map(this::toAppVO)
                .collect(Collectors.toList());
    }

    private AdminActivityVO toAdminVO(SysOpsActivity entity) {
        String stored = entity.getCoverUrl();
        return AdminActivityVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .coverUrl(resolveDisplayUrl(entity.getId(), stored))
                .coverKey(stored)
                .linkUrl(entity.getLinkUrl())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .startAt(entity.getStartAt())
                .endAt(entity.getEndAt())
                .publishedAt(entity.getPublishedAt())
                .publishedBy(entity.getPublishedBy())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private AppActivityVO toAppVO(SysOpsActivity entity) {
        return AppActivityVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .coverUrl(resolveDisplayUrl(entity.getId(), entity.getCoverUrl()))
                .linkUrl(entity.getLinkUrl())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .build();
    }

    private String resolveDisplayUrl(Long id, String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        String value = stored.trim();
        if (mediaUrlService.isExternalHttpUrl(value)
                || value.startsWith("data:")
                || value.startsWith("blob:")) {
            return value;
        }
        long v = System.currentTimeMillis() / 60_000L;
        return "/media/activities/" + id + "?v=" + v;
    }

    private SysOpsActivity requireActivity(Long id) {
        SysOpsActivity entity = activityMapper.selectOneById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new CustomException(404, "activity not found");
        }
        return entity;
    }

    private static String normalizeOptionalText(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String t = text.trim();
        if (t.length() > maxLen) {
            throw new CustomException(400, "text too long");
        }
        return t;
    }

    private static int normalizeSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String normalizeCoverRef(String ref) {
        if (!StringUtils.hasText(ref)) {
            throw new CustomException(400, "coverUrl required");
        }
        String u = ref.trim();
        if (u.length() > 1024) {
            throw new CustomException(400, "coverUrl too long");
        }
        if (u.startsWith("/media/")) {
            throw new CustomException(400, "please upload image first");
        }
        if (mediaUrlService.isExternalHttpUrl(u)) {
            return u;
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            throw new CustomException(400, "please upload image first");
        }
        return u;
    }

    private static String normalizeHttpUrl(String url, String field) {
        if (!StringUtils.hasText(url)) {
            throw new CustomException(400, field + " required");
        }
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new CustomException(400, field + " must be http/https URL");
        }
        if (u.length() > 1024) {
            throw new CustomException(400, field + " too long");
        }
        return u;
    }

    private static String normalizeOptionalHttpUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return normalizeHttpUrl(url, "linkUrl");
    }

    private static Date toDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }

    private static void validateWindow(Date startAt, Date endAt) {
        if (startAt != null && endAt != null && endAt.before(startAt)) {
            throw new CustomException(400, "endAt must be after startAt");
        }
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
