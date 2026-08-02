package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminRecommendDTO;
import com.linkx.server.controller.admin.dto.AdminRecommendQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRecommendVO;
import com.linkx.server.entity.admin.SysOpsRecommend;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysOpsRecommendMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.impl.AdminRecommendServiceImpl;
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
@DisplayName("AdminRecommendService 推荐位")
class AdminRecommendServiceTest {

    @Mock SysOpsRecommendMapper recommendMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;

    private AdminRecommendServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminRecommendServiceImpl(
                recommendMapper, fileStorageService, mediaUrlService, objectKeyOwnershipService);
    }

    private SysOpsRecommend draft(Long id) {
        return SysOpsRecommend.builder()
                .id(id)
                .slotCode(SysOpsRecommend.SLOT_DISCOVER)
                .title("Featured")
                .subtitle("Try this")
                .imageUrl("recommends/a.png")
                .linkUrl("https://example.com")
                .sortOrder(1)
                .status(SysOpsRecommend.STATUS_DRAFT)
                .deleted(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private AdminRecommendDTO validDto() {
        AdminRecommendDTO dto = new AdminRecommendDTO();
        dto.setSlotCode("discover");
        dto.setTitle(" Featured ");
        dto.setSubtitle(" Sub ");
        dto.setImageUrl("recommends/a.png");
        dto.setLinkUrl("https://example.com/x");
        dto.setSortOrder(2);
        dto.setStartAt(System.currentTimeMillis() - 1000);
        dto.setEndAt(System.currentTimeMillis() + 86_400_000);
        return dto;
    }

    @Test
    @DisplayName("列表分页与筛选")
    void list_ok() {
        when(recommendMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(recommendMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draft(1L)));
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        AdminRecommendQueryDTO q = new AdminRecommendQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("Featured");
        q.setRecommendStatus(SysOpsRecommend.STATUS_DRAFT);
        q.setSlotCode("discover");
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());

        var page = service.list(q);
        assertEquals(1, page.getTotal());
        assertEquals("Featured", page.getItems().get(0).getTitle());
        assertTrue(page.getItems().get(0).getImageUrl().startsWith("/media/recommends/1"));
    }

    @Test
    @DisplayName("详情与创建")
    void detail_and_create() {
        when(recommendMapper.selectOneById(1L)).thenReturn(draft(1L));
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
        assertEquals(1L, service.detail(1L).getId());

        when(recommendMapper.insert(any(SysOpsRecommend.class))).thenAnswer(inv -> {
            SysOpsRecommend e = inv.getArgument(0);
            e.setId(9L);
            return 1;
        });
        AdminRecommendVO created = service.create(validDto(), 7L);
        assertEquals(9L, created.getId());
        assertEquals(SysOpsRecommend.STATUS_DRAFT, created.getStatus());
    }

    @Test
    @DisplayName("更新/删除草稿；已发布拒绝")
    void update_delete_guards() {
        SysOpsRecommend draft = draft(2L);
        when(recommendMapper.selectOneById(2L)).thenReturn(draft);
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        service.update(2L, validDto(), 1L);
        verify(recommendMapper).update(draft);

        service.delete(2L, 1L);
        assertEquals(1, draft.getDeleted());

        SysOpsRecommend published = draft(3L);
        published.setStatus(SysOpsRecommend.STATUS_PUBLISHED);
        when(recommendMapper.selectOneById(3L)).thenReturn(published);
        assertThrows(CustomException.class, () -> service.update(3L, validDto(), 1L));
        assertThrows(CustomException.class, () -> service.delete(3L, 1L));
    }

    @Test
    @DisplayName("发布与下线")
    void publish_unpublish() {
        SysOpsRecommend draft = draft(4L);
        when(recommendMapper.selectOneById(4L)).thenReturn(draft);
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);

        AdminRecommendVO pub = service.publish(4L, 8L);
        assertEquals(SysOpsRecommend.STATUS_PUBLISHED, pub.getStatus());
        assertThrows(CustomException.class, () -> service.publish(4L, 8L));

        AdminRecommendVO un = service.unpublish(4L, 8L);
        assertEquals(SysOpsRecommend.STATUS_UNPUBLISHED, un.getStatus());
        assertThrows(CustomException.class, () -> service.unpublish(4L, 8L));
    }

    @Test
    @DisplayName("上传图片与客户端已发布列表")
    void upload_and_listPublished() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0});
        when(fileStorageService.uploadFile(eq(file), isNull())).thenReturn("recommends/u.png");
        when(mediaUrlService.resolveAvatar("recommends/u.png")).thenReturn("https://cdn/u.png");

        var upload = service.uploadImage(file, 11L);
        assertEquals("recommends/u.png", upload.getObjectKey());
        verify(objectKeyOwnershipService).claim(11L, "recommends/u.png");
        assertThrows(CustomException.class, () -> service.uploadImage(null, 1L));

        when(recommendMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draft(5L)));
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
        assertEquals(1, service.listPublishedForClient("discover").size());
    }

    @Test
    @DisplayName("校验：slot/时间窗/图片引用")
    void validation() {
        AdminRecommendDTO dto = validDto();
        dto.setSlotCode("invalid");
        assertThrows(CustomException.class, () -> service.create(dto, 1L));

        AdminRecommendDTO badWin = validDto();
        badWin.setStartAt(2000L);
        badWin.setEndAt(1000L);
        assertThrows(CustomException.class, () -> service.create(badWin, 1L));

        AdminRecommendDTO mediaPath = validDto();
        mediaPath.setImageUrl("/media/recommends/1");
        assertThrows(CustomException.class, () -> service.create(mediaPath, 1L));

        AdminRecommendDTO badLink = validDto();
        badLink.setLinkUrl("ftp://bad");
        assertThrows(CustomException.class, () -> service.create(badLink, 1L));

        when(recommendMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(99L));

        SysOpsRecommend deleted = draft(100L);
        deleted.setDeleted(1);
        when(recommendMapper.selectOneById(100L)).thenReturn(deleted);
        assertThrows(CustomException.class, () -> service.detail(100L));
    }

    @Test
    @DisplayName("外部图片 URL 与空 linkUrl")
    void externalImage_and_optionalLink() {
        when(recommendMapper.insert(any(SysOpsRecommend.class))).thenAnswer(inv -> {
            SysOpsRecommend e = inv.getArgument(0);
            e.setId(20L);
            return 1;
        });
        when(mediaUrlService.isExternalHttpUrl("https://cdn/img.png")).thenReturn(true);

        AdminRecommendDTO dto = validDto();
        dto.setImageUrl("https://cdn/img.png");
        dto.setLinkUrl(null);
        AdminRecommendVO vo = service.create(dto, 1L);
        assertEquals("https://cdn/img.png", vo.getImageKey());
    }
}
