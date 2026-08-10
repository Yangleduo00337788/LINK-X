package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ImageUploadValidator;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminRecommendDTO;
import com.linkx.server.controller.admin.dto.AdminRecommendQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRecommendUploadVO;
import com.linkx.server.controller.admin.vo.AdminRecommendVO;
import com.linkx.server.controller.vo.AppRecommendVO;
import com.linkx.server.entity.admin.SysOpsRecommend;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysOpsRecommendMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.AdminRecommendService;
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
public class AdminRecommendServiceImpl implements AdminRecommendService {

    private static final Set<String> STATUSES = Set.of(
            SysOpsRecommend.STATUS_DRAFT,
            SysOpsRecommend.STATUS_PUBLISHED,
            SysOpsRecommend.STATUS_UNPUBLISHED
    );
    private static final Set<String> SLOT_CODES = Set.of(
            SysOpsRecommend.SLOT_DISCOVER,
            SysOpsRecommend.SLOT_CHAT_SIDEBAR,
            SysOpsRecommend.SLOT_MOMENTS
    );

    private final SysOpsRecommendMapper recommendMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;

    @Override
    public PageResultVO<AdminRecommendVO> list(AdminRecommendQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysOpsRecommend::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysOpsRecommend::getTitle).like(kw)
                        .or(SysOpsRecommend::getSubtitle).like(kw)
                        .or(SysOpsRecommend::getImageUrl).like(kw)
                        .or(SysOpsRecommend::getLinkUrl).like(kw);
            });
        }
        if (StringUtils.hasText(query.getRecommendStatus())) {
            String status = query.getRecommendStatus().trim();
            if (STATUSES.contains(status)) {
                qw.and(SysOpsRecommend::getStatus).eq(status);
            }
        }
        if (StringUtils.hasText(query.getSlotCode())) {
            String slot = query.getSlotCode().trim().toLowerCase(Locale.ROOT);
            if (SLOT_CODES.contains(slot)) {
                qw.and(SysOpsRecommend::getSlotCode).eq(slot);
            }
        }
        if (query.getStartTime() != null) {
            qw.and(SysOpsRecommend::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysOpsRecommend::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(SysOpsRecommend::getSortOrder, true)
                .orderBy(SysOpsRecommend::getUpdateTime, false);
        long total = recommendMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminRecommendVO> items = recommendMapper.selectListByQuery(qw).stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminRecommendVO detail(Long id) {
        return toAdminVO(requireRecommend(id));
    }

    @Override
    @Transactional
    public AdminRecommendVO create(AdminRecommendDTO dto, Long operatorId) {
        Date now = new Date();
        Date startAt = toDate(dto.getStartAt());
        Date endAt = toDate(dto.getEndAt());
        validateWindow(startAt, endAt);
        SysOpsRecommend entity = SysOpsRecommend.builder()
                .slotCode(normalizeSlotCode(dto.getSlotCode()))
                .title(normalizeOptionalText(dto.getTitle(), 128))
                .subtitle(normalizeOptionalText(dto.getSubtitle(), 255))
                .imageUrl(normalizeImageRef(dto.getImageUrl()))
                .linkUrl(normalizeOptionalHttpUrl(dto.getLinkUrl()))
                .sortOrder(normalizeSortOrder(dto.getSortOrder()))
                .status(SysOpsRecommend.STATUS_DRAFT)
                .startAt(startAt)
                .endAt(endAt)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        recommendMapper.insert(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public AdminRecommendVO update(Long id, AdminRecommendDTO dto, Long operatorId) {
        SysOpsRecommend entity = requireRecommend(id);
        if (SysOpsRecommend.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published recommend cannot be edited, unpublish first");
        }
        Date startAt = toDate(dto.getStartAt());
        Date endAt = toDate(dto.getEndAt());
        validateWindow(startAt, endAt);
        entity.setSlotCode(normalizeSlotCode(dto.getSlotCode()));
        entity.setTitle(normalizeOptionalText(dto.getTitle(), 128));
        entity.setSubtitle(normalizeOptionalText(dto.getSubtitle(), 255));
        entity.setImageUrl(normalizeImageRef(dto.getImageUrl()));
        entity.setLinkUrl(normalizeOptionalHttpUrl(dto.getLinkUrl()));
        entity.setSortOrder(normalizeSortOrder(dto.getSortOrder()));
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        recommendMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysOpsRecommend entity = requireRecommend(id);
        if (SysOpsRecommend.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published recommend cannot be deleted, unpublish first");
        }
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        recommendMapper.update(entity);
    }

    @Override
    @Transactional
    public AdminRecommendVO publish(Long id, Long operatorId) {
        SysOpsRecommend entity = requireRecommend(id);
        if (SysOpsRecommend.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "recommend already published");
        }
        Date now = new Date();
        entity.setStatus(SysOpsRecommend.STATUS_PUBLISHED);
        entity.setPublishedAt(now);
        entity.setPublishedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        recommendMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public AdminRecommendVO unpublish(Long id, Long operatorId) {
        SysOpsRecommend entity = requireRecommend(id);
        if (!SysOpsRecommend.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "only published recommend can be unpublished");
        }
        Date now = new Date();
        entity.setStatus(SysOpsRecommend.STATUS_UNPUBLISHED);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        recommendMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    public AdminRecommendUploadVO uploadImage(MultipartFile file, Long operatorId) {
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
        return AdminRecommendUploadVO.builder()
                .objectKey(objectKey)
                .url(previewUrl)
                .build();
    }

    @Override
    public List<AppRecommendVO> listPublishedForClient(String slotCode) {
        Date now = new Date();
        QueryWrapper qw = QueryWrapper.create()
                .where(SysOpsRecommend::getDeleted).eq(0)
                .and(SysOpsRecommend::getStatus).eq(SysOpsRecommend.STATUS_PUBLISHED);
        if (StringUtils.hasText(slotCode)) {
            String slot = slotCode.trim().toLowerCase(Locale.ROOT);
            if (SLOT_CODES.contains(slot)) {
                qw.and(SysOpsRecommend::getSlotCode).eq(slot);
            }
        }
        qw.and((QueryWrapper w) -> {
            w.where(SysOpsRecommend::getStartAt).isNull()
                    .or(SysOpsRecommend::getStartAt).le(now);
        });
        qw.and((QueryWrapper w) -> {
            w.where(SysOpsRecommend::getEndAt).isNull()
                    .or(SysOpsRecommend::getEndAt).ge(now);
        });
        qw.orderBy(SysOpsRecommend::getSortOrder, true)
                .orderBy(SysOpsRecommend::getId, true);
        return recommendMapper.selectListByQuery(qw).stream()
                .map(this::toAppVO)
                .collect(Collectors.toList());
    }

    private AdminRecommendVO toAdminVO(SysOpsRecommend entity) {
        String stored = entity.getImageUrl();
        return AdminRecommendVO.builder()
                .id(entity.getId())
                .slotCode(entity.getSlotCode())
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle())
                .imageUrl(resolveDisplayUrl(entity.getId(), stored))
                .imageKey(stored)
                .linkUrl(entity.getLinkUrl())
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

    private AppRecommendVO toAppVO(SysOpsRecommend entity) {
        return AppRecommendVO.builder()
                .id(entity.getId())
                .slotCode(entity.getSlotCode())
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle())
                .imageUrl(resolveDisplayUrl(entity.getId(), entity.getImageUrl()))
                .linkUrl(entity.getLinkUrl())
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
        return "/media/recommends/" + id + "?v=" + v;
    }

    private SysOpsRecommend requireRecommend(Long id) {
        SysOpsRecommend entity = recommendMapper.selectOneById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new CustomException(404, "recommend not found");
        }
        return entity;
    }

    private static String normalizeSlotCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            throw new CustomException(400, "slotCode required");
        }
        String slot = slotCode.trim().toLowerCase(Locale.ROOT);
        if (!SLOT_CODES.contains(slot)) {
            throw new CustomException(400, "invalid slotCode");
        }
        return slot;
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

    private String normalizeImageRef(String ref) {
        if (!StringUtils.hasText(ref)) {
            throw new CustomException(400, "imageUrl required");
        }
        String u = ref.trim();
        if (u.length() > 1024) {
            throw new CustomException(400, "imageUrl too long");
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
