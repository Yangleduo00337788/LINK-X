package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminHomepageSectionReorderDTO;
import com.linkx.server.entity.admin.SysHomepageSection;
import com.linkx.server.mapper.admin.SysBannerMapper;
import com.linkx.server.mapper.admin.SysHomepageSectionMapper;
import com.linkx.server.mapper.admin.SysOpsActivityMapper;
import com.linkx.server.mapper.admin.SysOpsRecommendMapper;
import com.linkx.server.mapper.admin.SysAdminNoticeMapper;
import com.linkx.server.service.admin.impl.AdminHomepageSectionServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminHomepageSectionService 首页编排")
class AdminHomepageSectionServiceTest {

    @Mock SysHomepageSectionMapper sectionMapper;
    @Mock SysBannerMapper bannerMapper;
    @Mock SysOpsRecommendMapper recommendMapper;
    @Mock SysOpsActivityMapper activityMapper;
    @Mock SysAdminNoticeMapper noticeMapper;
    @Mock AdminBannerService adminBannerService;
    @Mock AdminRecommendService adminRecommendService;
    @Mock AdminActivityService adminActivityService;

    private AdminHomepageSectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminHomepageSectionServiceImpl(
                sectionMapper, bannerMapper, recommendMapper, activityMapper, noticeMapper,
                adminBannerService, adminRecommendService, adminActivityService);
    }

    @Test
    @DisplayName("列表与发布数")
    void listSections_ok() {
        SysHomepageSection section = SysHomepageSection.builder()
                .id(1L)
                .sectionType(SysHomepageSection.TYPE_BANNER)
                .sectionKey("home")
                .title("Banner")
                .enabled(true)
                .sortOrder(10)
                .deleted(0)
                .build();
        when(sectionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(section));
        when(bannerMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);

        var list = service.listSections();
        assertEquals(1, list.size());
        assertEquals(2L, list.get(0).getPublishedCount());
        assertEquals("/admin/banners", list.get(0).getManagePath());
    }

    @Test
    @DisplayName("批量排序")
    void reorder_ok() {
        SysHomepageSection section = SysHomepageSection.builder().id(9L).deleted(0).build();
        when(sectionMapper.selectOneById(9L)).thenReturn(section);

        AdminHomepageSectionReorderDTO dto = new AdminHomepageSectionReorderDTO();
        AdminHomepageSectionReorderDTO.Item item = new AdminHomepageSectionReorderDTO.Item();
        item.setId(9L);
        item.setSortOrder(5);
        item.setEnabled(false);
        dto.setItems(List.of(item));

        service.reorder(dto, 1L);
        assertEquals(5, section.getSortOrder());
        assertFalse(section.getEnabled());
        verify(sectionMapper).update(section);
    }
}
