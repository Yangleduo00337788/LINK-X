package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ImageUploadValidator;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.AdminKeywordQuery;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminBannerDTO;
import com.linkx.server.controller.admin.dto.AdminBannerQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBannerUploadVO;
import com.linkx.server.controller.admin.vo.AdminBannerVO;
import com.linkx.server.controller.vo.AppBannerVO;
import com.linkx.server.entity.admin.SysBanner;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysBannerMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.AdminBannerService;
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
public class AdminBannerServiceImpl implements AdminBannerService {

    private static final Set<String> STATUSES = Set.of(
            SysBanner.STATUS_DRAFT,
            SysBanner.STATUS_PUBLISHED,
            SysBanner.STATUS_UNPUBLISHED
    );
    private static final Set<String> POSITIONS = Set.of(
            SysBanner.POSITION_HOME,
            SysBanner.POSITION_LOGIN
    );

    private final SysBannerMapper bannerMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;

    @Override
    public PageResultVO<AdminBannerVO> list(AdminBannerQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysBanner::getDeleted).eq(0);
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
            qw.and((QueryWrapper w) -> {
                w.where(SysBanner::getTitle).like(kw)
                        .or(SysBanner::getImageUrl).like(kw)
                        .or(SysBanner::getLinkUrl).like(kw);
            });
        }
        if (StringUtils.hasText(query.getBannerStatus())) {
            String status = query.getBannerStatus().trim();
            if (STATUSES.contains(status)) {
                qw.and(SysBanner::getStatus).eq(status);
            }
        }
        if (StringUtils.hasText(query.getPosition())) {
            String position = query.getPosition().trim().toLowerCase(Locale.ROOT);
            if (POSITIONS.contains(position)) {
                qw.and(SysBanner::getPosition).eq(position);
            }
        }
        if (query.getStartTime() != null) {
            qw.and(SysBanner::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysBanner::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(SysBanner::getSortOrder, true)
                .orderBy(SysBanner::getUpdateTime, false);
        long total = bannerMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminBannerVO> items = bannerMapper.selectListByQuery(qw).stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminBannerVO detail(Long id) {
        return toAdminVO(requireBanner(id));
    }

    @Override
    @Transactional
    public AdminBannerVO create(AdminBannerDTO dto, Long operatorId) {
        Date now = new Date();
        Date startAt = toDate(dto.getStartAt());
        Date endAt = toDate(dto.getEndAt());
        validateWindow(startAt, endAt);
        SysBanner entity = SysBanner.builder()
                .title(normalizeTitle(dto.getTitle()))
                .imageUrl(normalizeImageRef(dto.getImageUrl()))
                .linkUrl(normalizeOptionalHttpUrl(dto.getLinkUrl()))
                .position(normalizePosition(dto.getPosition()))
                .sortOrder(normalizeSortOrder(dto.getSortOrder()))
                .status(SysBanner.STATUS_DRAFT)
                .startAt(startAt)
                .endAt(endAt)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        bannerMapper.insert(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public AdminBannerVO update(Long id, AdminBannerDTO dto, Long operatorId) {
        SysBanner entity = requireBanner(id);
        if (SysBanner.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published banner cannot be edited, unpublish first");
        }
        Date startAt = toDate(dto.getStartAt());
        Date endAt = toDate(dto.getEndAt());
        validateWindow(startAt, endAt);
        entity.setTitle(normalizeTitle(dto.getTitle()));
        entity.setImageUrl(normalizeImageRef(dto.getImageUrl()));
        entity.setLinkUrl(normalizeOptionalHttpUrl(dto.getLinkUrl()));
        entity.setPosition(normalizePosition(dto.getPosition()));
        entity.setSortOrder(normalizeSortOrder(dto.getSortOrder()));
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        bannerMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysBanner entity = requireBanner(id);
        if (SysBanner.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published banner cannot be deleted, unpublish first");
        }
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        bannerMapper.update(entity);
    }

    @Override
    @Transactional
    public AdminBannerVO publish(Long id, Long operatorId) {
        SysBanner entity = requireBanner(id);
        if (SysBanner.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "banner already published");
        }
        Date now = new Date();
        entity.setStatus(SysBanner.STATUS_PUBLISHED);
        entity.setPublishedAt(now);
        entity.setPublishedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        bannerMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    @Transactional
    public AdminBannerVO unpublish(Long id, Long operatorId) {
        SysBanner entity = requireBanner(id);
        if (!SysBanner.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "only published banner can be unpublished");
        }
        Date now = new Date();
        entity.setStatus(SysBanner.STATUS_UNPUBLISHED);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        bannerMapper.update(entity);
        return toAdminVO(entity);
    }

    @Override
    public AdminBannerUploadVO uploadImage(MultipartFile file, Long operatorId) {
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
        return AdminBannerUploadVO.builder()
                .objectKey(objectKey)
                .url(previewUrl)
                .build();
    }

    @Override
    public List<AppBannerVO> listPublishedForClient(String position) {
        Date now = new Date();
        QueryWrapper qw = QueryWrapper.create()
                .where(SysBanner::getDeleted).eq(0)
                .and(SysBanner::getStatus).eq(SysBanner.STATUS_PUBLISHED);
        if (StringUtils.hasText(position)) {
            String p = position.trim().toLowerCase(Locale.ROOT);
            if (POSITIONS.contains(p)) {
                qw.and(SysBanner::getPosition).eq(p);
            }
        }
        // 时效：start_at 为空或已开始；end_at 为空或未结束
        qw.and((QueryWrapper w) -> {
            w.where(SysBanner::getStartAt).isNull()
                    .or(SysBanner::getStartAt).le(now);
        });
        qw.and((QueryWrapper w) -> {
            w.where(SysBanner::getEndAt).isNull()
                    .or(SysBanner::getEndAt).ge(now);
        });
        qw.orderBy(SysBanner::getSortOrder, true)
                .orderBy(SysBanner::getId, true);
        return bannerMapper.selectListByQuery(qw).stream()
                .map(this::toAppVO)
                .collect(Collectors.toList());
    }

    private AdminBannerVO toAdminVO(SysBanner entity) {
        String stored = entity.getImageUrl();
        return AdminBannerVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .imageUrl(resolveDisplayUrl(entity.getId(), stored))
                .imageKey(stored)
                .linkUrl(entity.getLinkUrl())
                .position(entity.getPosition())
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

    private AppBannerVO toAppVO(SysBanner entity) {
        return AppBannerVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .imageUrl(resolveDisplayUrl(entity.getId(), entity.getImageUrl()))
                .linkUrl(entity.getLinkUrl())
                .position(entity.getPosition())
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
        // 同源代理，避免浏览器直连 MinIO 预签名地址失败
        long v = System.currentTimeMillis() / 60_000L;
        return "/media/banners/" + id + "?v=" + v;
    }

    private SysBanner requireBanner(Long id) {
        SysBanner entity = bannerMapper.selectOneById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new CustomException(404, "banner not found");
        }
        return entity;
    }

    private static String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new CustomException(400, "title required");
        }
        return title.trim();
    }

    private static String normalizePosition(String position) {
        if (!StringUtils.hasText(position)) {
            throw new CustomException(400, "position required");
        }
        String p = position.trim().toLowerCase(Locale.ROOT);
        if (!POSITIONS.contains(p)) {
            throw new CustomException(400, "invalid position");
        }
        return p;
    }

    private static int normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return 0;
        }
        return sortOrder;
    }

    /** 图片：对象 key（上传返回）或兼容历史外链 */
    private String normalizeImageRef(String ref) {
        if (!StringUtils.hasText(ref)) {
            throw new CustomException(400, "imageUrl required");
        }
        String u = ref.trim();
        if (u.length() > 1024) {
            throw new CustomException(400, "imageUrl too long");
        }
        // 展示路径误提交时拒绝（应提交 imageKey / objectKey）
        if (u.startsWith("/media/")) {
            throw new CustomException(400, "please upload image first");
        }
        if (mediaUrlService.isExternalHttpUrl(u)) {
            return u;
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            // 本系统 MinIO 预签名 URL：不允许入库，要求 object key
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
