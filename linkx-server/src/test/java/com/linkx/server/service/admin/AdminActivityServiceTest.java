package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminActivityDTO;
import com.linkx.server.controller.admin.dto.AdminActivityQueryDTO;
import com.linkx.server.controller.admin.vo.AdminActivityVO;
import com.linkx.server.controller.vo.AppActivityVO;
import com.linkx.server.entity.admin.SysOpsActivity;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysOpsActivityMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.admin.impl.AdminActivityServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminActivityService 活动管理")
class AdminActivityServiceTest {

    @Mock SysOpsActivityMapper activityMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;

    private AdminActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminActivityServiceImpl(
                activityMapper, fileStorageService, mediaUrlService, objectKeyOwnershipService);
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
    }

    private SysOpsActivity draft(Long id) {
        return SysOpsActivity.builder()
                .id(id)
                .title("Summer Sale")
                .coverUrl("activities/cover.png")
                .linkUrl("https://example.com/promo")
                .description("Big discount")
                .sortOrder(1)
                .status(SysOpsActivity.STATUS_DRAFT)
                .deleted(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private AdminActivityDTO validDto() {
        AdminActivityDTO dto = new AdminActivityDTO();
        dto.setTitle(" Summer Sale ");
        dto.setCoverUrl("activities/cover.png");
        dto.setLinkUrl("https://example.com/promo");
        dto.setDescription("Big discount");
        dto.setSortOrder(2);
        dto.setStartAt(System.currentTimeMillis() - 86_400_000);
        dto.setEndAt(System.currentTimeMillis() + 86_400_000);
        return dto;
    }

    @Nested
    @DisplayName("查询")
    class Query {
        @Test
        @DisplayName("list 关键词与状态筛选")
        void list_withFilters() {
            when(activityMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(activityMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draft(1L)));

            AdminActivityQueryDTO q = new AdminActivityQueryDTO();
            q.setPage(1);
            q.setSize(10);
            q.setKeyword("Summer");
            q.setActivityStatus(SysOpsActivity.STATUS_DRAFT);
            q.setStartTime(1L);
            q.setEndTime(System.currentTimeMillis());

            var page = service.list(q);
            assertEquals(1, page.getTotal());
            assertEquals("Summer Sale", page.getItems().get(0).getTitle());
            assertTrue(page.getItems().get(0).getCoverUrl().startsWith("/media/activities/1"));
        }

        @Test
        @DisplayName("detail 成功")
        void detail_ok() {
            when(activityMapper.selectOneById(1L)).thenReturn(draft(1L));
            assertEquals(1L, service.detail(1L).getId());
        }

        @Test
        @DisplayName("detail 404")
        void detail_notFound() {
            when(activityMapper.selectOneById(9L)).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.detail(9L));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("listPublishedForClient 已发布活动")
        void listPublishedForApp() {
            SysOpsActivity pub = draft(5L);
            pub.setStatus(SysOpsActivity.STATUS_PUBLISHED);
            pub.setStartAt(new Date(System.currentTimeMillis() - 86_400_000));
            pub.setEndAt(new Date(System.currentTimeMillis() + 86_400_000));
            when(activityMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(pub));

            List<AppActivityVO> items = service.listPublishedForClient();
            assertEquals(1, items.size());
            assertEquals("Summer Sale", items.get(0).getTitle());
        }
    }

    @Nested
    @DisplayName("写操作")
    class Write {
        @Test
        @DisplayName("create 草稿")
        void create_ok() {
            when(activityMapper.insert(any(SysOpsActivity.class))).thenAnswer(inv -> {
                SysOpsActivity e = inv.getArgument(0);
                e.setId(9L);
                return 1;
            });
            AdminActivityVO created = service.create(validDto(), 7L);
            assertEquals(9L, created.getId());
            assertEquals(SysOpsActivity.STATUS_DRAFT, created.getStatus());
        }

        @Test
        @DisplayName("update 草稿成功")
        void update_draft() {
            SysOpsActivity entity = draft(2L);
            when(activityMapper.selectOneById(2L)).thenReturn(entity);
            service.update(2L, validDto(), 1L);
            verify(activityMapper).update(entity);
            assertEquals("Summer Sale", entity.getTitle());
        }

        @Test
        @DisplayName("update 已发布拒绝")
        void update_publishedRejected() {
            SysOpsActivity published = draft(3L);
            published.setStatus(SysOpsActivity.STATUS_PUBLISHED);
            when(activityMapper.selectOneById(3L)).thenReturn(published);
            assertThrows(CustomException.class, () -> service.update(3L, validDto(), 1L));
        }

        @Test
        @DisplayName("delete 草稿软删")
        void delete_draft() {
            SysOpsActivity entity = draft(4L);
            when(activityMapper.selectOneById(4L)).thenReturn(entity);
            service.delete(4L, 1L);
            assertEquals(1, entity.getDeleted());
            verify(activityMapper).update(entity);
        }

        @Test
        @DisplayName("delete 已发布拒绝")
        void delete_publishedRejected() {
            SysOpsActivity published = draft(5L);
            published.setStatus(SysOpsActivity.STATUS_PUBLISHED);
            when(activityMapper.selectOneById(5L)).thenReturn(published);
            assertThrows(CustomException.class, () -> service.delete(5L, 1L));
        }

        @Test
        @DisplayName("publish 与 unpublish")
        void publish_unpublish() {
            SysOpsActivity entity = draft(6L);
            when(activityMapper.selectOneById(6L)).thenReturn(entity);

            AdminActivityVO pub = service.publish(6L, 8L);
            assertEquals(SysOpsActivity.STATUS_PUBLISHED, pub.getStatus());
            assertNotNull(pub.getPublishedAt());
            assertThrows(CustomException.class, () -> service.publish(6L, 8L));

            AdminActivityVO un = service.unpublish(6L, 8L);
            assertEquals(SysOpsActivity.STATUS_UNPUBLISHED, un.getStatus());
            assertThrows(CustomException.class, () -> service.unpublish(6L, 8L));
        }
    }

    @Nested
    @DisplayName("上传与校验")
    class UploadAndValidation {
        @Test
        @DisplayName("uploadCover 成功")
        void uploadCover_success() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.png", "image/png",
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0});
            when(fileStorageService.uploadFile(eq(file), isNull())).thenReturn("activities/u.png");
            when(mediaUrlService.resolveAvatar("activities/u.png")).thenReturn("https://cdn/u.png");

            var upload = service.uploadImage(file, 11L);
            assertEquals("activities/u.png", upload.getObjectKey());
            assertEquals("https://cdn/u.png", upload.getUrl());
            verify(objectKeyOwnershipService).claim(11L, "activities/u.png");
        }

        @Test
        @DisplayName("uploadCover 空文件拒绝")
        void uploadCover_empty() {
            assertThrows(CustomException.class, () -> service.uploadImage(null, 1L));
        }

        @Test
        @DisplayName("create 时间窗非法")
        void create_badWindow() {
            AdminActivityDTO dto = validDto();
            dto.setStartAt(2000L);
            dto.setEndAt(1000L);
            assertThrows(CustomException.class, () -> service.create(dto, 1L));
        }

        @Test
        @DisplayName("create 封面引用 /media 拒绝")
        void create_mediaPathRejected() {
            AdminActivityDTO dto = validDto();
            dto.setCoverUrl("/media/activities/1");
            assertThrows(CustomException.class, () -> service.create(dto, 1L));
        }
    }
}
