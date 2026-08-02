package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminBannerDTO;
import com.linkx.server.controller.admin.dto.AdminBannerQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBannerVO;
import com.linkx.server.controller.vo.AppBannerVO;
import com.linkx.server.entity.admin.SysBanner;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysBannerMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.impl.AdminBannerServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBannerService Banner 管理")
class AdminBannerServiceTest {

    @Mock SysBannerMapper bannerMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;

    private AdminBannerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminBannerServiceImpl(
                bannerMapper, fileStorageService, mediaUrlService, objectKeyOwnershipService);
    }

    private SysBanner draftBanner(Long id) {
        return SysBanner.builder()
                .id(id)
                .title("Promo")
                .imageUrl("banners/a.png")
                .linkUrl("https://example.com")
                .position(SysBanner.POSITION_HOME)
                .sortOrder(1)
                .status(SysBanner.STATUS_DRAFT)
                .deleted(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private AdminBannerDTO validDto() {
        AdminBannerDTO dto = new AdminBannerDTO();
        dto.setTitle(" Promo ");
        dto.setImageUrl("banners/a.png");
        dto.setLinkUrl("https://example.com/x");
        dto.setPosition("HOME");
        dto.setSortOrder(3);
        dto.setStartAt(System.currentTimeMillis() - 1000);
        dto.setEndAt(System.currentTimeMillis() + 86_400_000);
        return dto;
    }

    @Test
    @DisplayName("列表分页与筛选")
    void list_ok() {
        when(bannerMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(bannerMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draftBanner(1L)));
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        AdminBannerQueryDTO q = new AdminBannerQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("Promo");
        q.setBannerStatus(SysBanner.STATUS_DRAFT);
        q.setPosition("home");
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());

        var page = service.list(q);
        assertEquals(1, page.getTotal());
        assertEquals("Promo", page.getItems().get(0).getTitle());
        assertTrue(page.getItems().get(0).getImageUrl().startsWith("/media/banners/1"));
    }

    @Test
    @DisplayName("详情与创建")
    void detail_and_create() {
        when(bannerMapper.selectOneById(1L)).thenReturn(draftBanner(1L));
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        AdminBannerVO detail = service.detail(1L);
        assertEquals(1L, detail.getId());

        when(bannerMapper.insert(any(SysBanner.class))).thenAnswer(inv -> {
            SysBanner e = inv.getArgument(0);
            e.setId(9L);
            return 1;
        });
        AdminBannerVO created = service.create(validDto(), 7L);
        assertEquals(9L, created.getId());
        assertEquals(SysBanner.STATUS_DRAFT, created.getStatus());
        verify(bannerMapper).insert(any(SysBanner.class));
    }

    @Test
    @DisplayName("更新/删除草稿；已发布拒绝")
    void update_delete_guards() {
        SysBanner draft = draftBanner(2L);
        when(bannerMapper.selectOneById(2L)).thenReturn(draft);
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        service.update(2L, validDto(), 1L);
        verify(bannerMapper).update(draft);

        service.delete(2L, 1L);
        assertEquals(1, draft.getDeleted());

        SysBanner published = draftBanner(3L);
        published.setStatus(SysBanner.STATUS_PUBLISHED);
        when(bannerMapper.selectOneById(3L)).thenReturn(published);
        assertThrows(CustomException.class, () -> service.update(3L, validDto(), 1L));
        assertThrows(CustomException.class, () -> service.delete(3L, 1L));
    }

    @Test
    @DisplayName("发布与下线")
    void publish_unpublish() {
        SysBanner draft = draftBanner(4L);
        when(bannerMapper.selectOneById(4L)).thenReturn(draft);
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        AdminBannerVO pub = service.publish(4L, 8L);
        assertEquals(SysBanner.STATUS_PUBLISHED, pub.getStatus());
        assertThrows(CustomException.class, () -> service.publish(4L, 8L));

        AdminBannerVO un = service.unpublish(4L, 8L);
        assertEquals(SysBanner.STATUS_UNPUBLISHED, un.getStatus());
        assertThrows(CustomException.class, () -> service.unpublish(4L, 8L));
    }

    @Test
    @DisplayName("上传图片与客户端已发布列表")
    void upload_and_listPublished() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0});
        when(fileStorageService.uploadFile(eq(file), isNull())).thenReturn("banners/u.png");
        when(mediaUrlService.resolveAvatar("banners/u.png")).thenReturn("https://cdn/u.png");

        var upload = service.uploadImage(file, 11L);
        assertEquals("banners/u.png", upload.getObjectKey());
        verify(objectKeyOwnershipService).claim(11L, "banners/u.png");

        assertThrows(CustomException.class, () -> service.uploadImage(null, 1L));

        when(bannerMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draftBanner(5L)));
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
        List<AppBannerVO> client = service.listPublishedForClient("home");
        assertEquals(1, client.size());
        assertEquals("Promo", client.get(0).getTitle());
    }

    @Test
    @DisplayName("校验：标题/展位/时间窗/图片引用")
    void validation() {
        AdminBannerDTO dto = validDto();
        dto.setTitle(" ");
        assertThrows(CustomException.class, () -> service.create(dto, 1L));

        AdminBannerDTO badPos = validDto();
        badPos.setPosition("footer");
        assertThrows(CustomException.class, () -> service.create(badPos, 1L));

        AdminBannerDTO badWin = validDto();
        badWin.setStartAt(2000L);
        badWin.setEndAt(1000L);
        assertThrows(CustomException.class, () -> service.create(badWin, 1L));

        AdminBannerDTO mediaPath = validDto();
        mediaPath.setImageUrl("/media/banners/1");
        assertThrows(CustomException.class, () -> service.create(mediaPath, 1L));

        when(bannerMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(99L));
    }
}
