package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.dto.AdminHomepageSectionReorderDTO;
import com.linkx.server.controller.admin.vo.AdminHomepageSectionVO;
import com.linkx.server.controller.vo.AppHomepageVO;
import com.linkx.server.entity.admin.SysAdminNotice;
import com.linkx.server.entity.admin.SysBanner;
import com.linkx.server.entity.admin.SysHomepageSection;
import com.linkx.server.entity.admin.SysOpsActivity;
import com.linkx.server.entity.admin.SysOpsRecommend;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysAdminNoticeMapper;
import com.linkx.server.mapper.admin.SysBannerMapper;
import com.linkx.server.mapper.admin.SysHomepageSectionMapper;
import com.linkx.server.mapper.admin.SysOpsActivityMapper;
import com.linkx.server.mapper.admin.SysOpsRecommendMapper;
import com.linkx.server.service.admin.AdminActivityService;
import com.linkx.server.service.admin.AdminBannerService;
import com.linkx.server.service.admin.AdminHomepageSectionService;
import com.linkx.server.service.admin.AdminRecommendService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminHomepageSectionServiceImpl implements AdminHomepageSectionService {

    private final SysHomepageSectionMapper sectionMapper;
    private final SysBannerMapper bannerMapper;
    private final SysOpsRecommendMapper recommendMapper;
    private final SysOpsActivityMapper activityMapper;
    private final SysAdminNoticeMapper noticeMapper;
    private final AdminBannerService adminBannerService;
    private final AdminRecommendService adminRecommendService;
    private final AdminActivityService adminActivityService;

    @Override
    public List<AdminHomepageSectionVO> listSections() {
        List<SysHomepageSection> sections = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysHomepageSection::getDeleted).eq(0)
                        .orderBy(SysHomepageSection::getSortOrder, true)
                        .orderBy(SysHomepageSection::getId, true));
        return sections.stream().map(this::toAdminVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void reorder(AdminHomepageSectionReorderDTO dto, Long operatorId) {
        Date now = new Date();
        for (AdminHomepageSectionReorderDTO.Item item : dto.getItems()) {
            SysHomepageSection section = requireSection(item.getId());
            section.setSortOrder(item.getSortOrder());
            if (item.getEnabled() != null) {
                section.setEnabled(item.getEnabled());
            }
            section.setUpdateTime(now);
            sectionMapper.update(section);
        }
    }

    @Override
    public AppHomepageVO buildClientHomepage() {
        List<SysHomepageSection> sections = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysHomepageSection::getDeleted).eq(0)
                        .and(SysHomepageSection::getEnabled).eq(true)
                        .orderBy(SysHomepageSection::getSortOrder, true)
                        .orderBy(SysHomepageSection::getId, true));
        List<AppHomepageVO.AppHomepageSectionVO> items = new ArrayList<>();
        for (SysHomepageSection section : sections) {
            Object payload = resolvePayload(section);
            if (payload == null) {
                continue;
            }
            if (payload instanceof List<?> list && list.isEmpty()) {
                continue;
            }
            items.add(AppHomepageVO.AppHomepageSectionVO.builder()
                    .sectionType(section.getSectionType())
                    .sectionKey(section.getSectionKey())
                    .title(section.getTitle())
                    .sortOrder(section.getSortOrder())
                    .payload(payload)
                    .build());
        }
        return AppHomepageVO.builder().sections(items).build();
    }

    private Object resolvePayload(SysHomepageSection section) {
        return switch (section.getSectionType()) {
            case SysHomepageSection.TYPE_BANNER ->
                    adminBannerService.listPublishedForClient(section.getSectionKey());
            case SysHomepageSection.TYPE_RECOMMEND ->
                    adminRecommendService.listPublishedForClient(section.getSectionKey());
            case SysHomepageSection.TYPE_ACTIVITY -> adminActivityService.listPublishedForClient();
            case SysHomepageSection.TYPE_NOTICE -> listPublishedNotices();
            default -> null;
        };
    }

    private List<SysAdminNotice> listPublishedNotices() {
        return noticeMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAdminNotice::getStatus).eq(SysAdminNotice.STATUS_PUBLISHED)
                        .and(SysAdminNotice::getTargetSide).eq(SysAdminNotice.TARGET_CLIENT)
                        .and(SysAdminNotice::getDeleted).eq(0)
                        .orderBy(SysAdminNotice::getPublishedAt, false)
                        .limit(0, 5));
    }

    private AdminHomepageSectionVO toAdminVO(SysHomepageSection section) {
        return AdminHomepageSectionVO.builder()
                .id(section.getId())
                .sectionType(section.getSectionType())
                .sectionKey(section.getSectionKey())
                .title(section.getTitle())
                .enabled(section.getEnabled())
                .sortOrder(section.getSortOrder())
                .publishedCount(countPublished(section))
                .managePath(resolveManagePath(section))
                .build();
    }

    private long countPublished(SysHomepageSection section) {
        return switch (section.getSectionType()) {
            case SysHomepageSection.TYPE_BANNER -> bannerMapper.selectCountByQuery(
                    QueryWrapper.create()
                            .where(SysBanner::getStatus).eq(SysBanner.STATUS_PUBLISHED)
                            .and(SysBanner::getPosition).eq(section.getSectionKey())
                            .and(SysBanner::getDeleted).eq(0));
            case SysHomepageSection.TYPE_RECOMMEND -> recommendMapper.selectCountByQuery(
                    QueryWrapper.create()
                            .where(SysOpsRecommend::getStatus).eq(SysOpsRecommend.STATUS_PUBLISHED)
                            .and(SysOpsRecommend::getSlotCode).eq(section.getSectionKey())
                            .and(SysOpsRecommend::getDeleted).eq(0));
            case SysHomepageSection.TYPE_ACTIVITY -> activityMapper.selectCountByQuery(
                    QueryWrapper.create()
                            .where(SysOpsActivity::getStatus).eq(SysOpsActivity.STATUS_PUBLISHED)
                            .and(SysOpsActivity::getDeleted).eq(0));
            case SysHomepageSection.TYPE_NOTICE -> noticeMapper.selectCountByQuery(
                    QueryWrapper.create()
                            .where(SysAdminNotice::getStatus).eq(SysAdminNotice.STATUS_PUBLISHED)
                            .and(SysAdminNotice::getTargetSide).eq(SysAdminNotice.TARGET_CLIENT)
                            .and(SysAdminNotice::getDeleted).eq(0));
            default -> 0L;
        };
    }

    private static String resolveManagePath(SysHomepageSection section) {
        return switch (section.getSectionType()) {
            case SysHomepageSection.TYPE_BANNER -> "/admin/banners";
            case SysHomepageSection.TYPE_RECOMMEND -> "/admin/recommends";
            case SysHomepageSection.TYPE_ACTIVITY -> "/admin/activities";
            case SysHomepageSection.TYPE_NOTICE -> "/admin/notices";
            default -> null;
        };
    }

    private SysHomepageSection requireSection(Long id) {
        SysHomepageSection section = sectionMapper.selectOneById(id);
        if (section == null || Integer.valueOf(1).equals(section.getDeleted())) {
            throw new CustomException(404, "homepage section not found");
        }
        return section;
    }
}
