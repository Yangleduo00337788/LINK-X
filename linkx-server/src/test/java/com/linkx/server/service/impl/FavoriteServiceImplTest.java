package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SaveFavoriteDTO;
import com.linkx.server.controller.dto.SaveFavoriteTagDTO;
import com.linkx.server.controller.vo.FavoriteStorageVO;
import com.linkx.server.controller.vo.FavoriteTagVO;
import com.linkx.server.controller.vo.FavoriteVO;
import com.linkx.server.entity.Favorite;
import com.linkx.server.entity.FavoriteStorage;
import com.linkx.server.entity.FavoriteTag;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.FavoriteStorageMapper;
import com.linkx.server.mapper.FavoriteTagMapper;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.AdminReviewService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FavoriteServiceImpl 收藏服务")
class FavoriteServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long OTHER_ID = 99L;
    private static final long FAV_ID = 1001L;
    private static final long TAG_ID = 2001L;

    @Mock FavoriteMapper favoriteMapper;
    @Mock FavoriteStorageMapper favoriteStorageMapper;
    @Mock FavoriteTagMapper favoriteTagMapper;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock ObjectProvider<AdminReviewService> adminReviewService;
    @Mock AdminReviewService reviewService;

    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        when(adminReviewService.getIfAvailable()).thenReturn(null);
        when(sensitiveWordService.filter(any())).thenAnswer(inv ->
                new SensitiveWordService.FilterResult(inv.getArgument(0), false, false, false, List.of()));
        doAnswer(inv -> {
            Favorite fav = inv.getArgument(0);
            if (fav.getId() == null) {
                fav.setId(FAV_ID);
            }
            return 1;
        }).when(favoriteMapper).insert(any(Favorite.class));
        doAnswer(inv -> {
            FavoriteTag tag = inv.getArgument(0);
            if (tag.getId() == null) {
                tag.setId(TAG_ID);
            }
            return 1;
        }).when(favoriteTagMapper).insert(any(FavoriteTag.class));
        service = new FavoriteServiceImpl(
                favoriteMapper, favoriteStorageMapper, favoriteTagMapper, sensitiveWordService, adminReviewService);
    }

    private FavoriteStorage storage(long usedBytes, int itemCount, int version) {
        return FavoriteStorage.builder()
                .userId(USER_ID)
                .usedBytes(usedBytes)
                .itemCount(itemCount)
                .quotaBytes(FavoriteStorage.DEFAULT_QUOTA_BYTES)
                .version(version)
                .build();
    }

    private Favorite ownedFavorite(long fileSize) {
        return Favorite.builder()
                .id(FAV_ID)
                .userId(USER_ID)
                .title("标题")
                .content("内容")
                .type("note")
                .tags("[\"工作\"]")
                .fileSize(fileSize)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private void stubStorageForDelta() {
        when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(0L, 0, 0));
        when(favoriteStorageMapper.casUpdateUsedBytes(eq(USER_ID), anyLong(), anyInt(), eq(0))).thenReturn(1);
    }

    @Nested
    @DisplayName("list 列表")
    class ListTests {

        @Test
        @DisplayName("空收藏时初始化存储并插入预设标签")
        void emptyListEnsuresStorageAndPresets() {
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(null);
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            List<FavoriteVO> result = service.list(USER_ID);

            assertTrue(result.isEmpty());
            verify(favoriteStorageMapper).insert(any(FavoriteStorage.class));
            verify(favoriteTagMapper, times(5)).insert(any(FavoriteTag.class));
        }
    }

    @Nested
    @DisplayName("get 详情")
    class GetTests {

        @Test
        @DisplayName("本人收藏可读")
        void ownedOk() {
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(ownedFavorite(0L));

            FavoriteVO vo = service.get(USER_ID, FAV_ID);

            assertEquals(FAV_ID, vo.getId());
            assertEquals("内容", vo.getContent());
        }

        @Test
        @DisplayName("不存在返回 404")
        void notFound() {
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(null);

            CustomException ex = assertThrows(CustomException.class, () -> service.get(USER_ID, FAV_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("非本人返回 403")
        void wrongOwner() {
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(
                    Favorite.builder().id(FAV_ID).userId(OTHER_ID).content("x").build());

            CustomException ex = assertThrows(CustomException.class, () -> service.get(USER_ID, FAV_ID));
            assertEquals(403, ex.getCode());
        }
    }

    @Nested
    @DisplayName("create 创建")
    class CreateTests {

        @Test
        @DisplayName("空内容返回 400")
        void emptyContent() {
            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("  ");

            CustomException ex = assertThrows(CustomException.class, () -> service.create(USER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("成功创建并 CAS 更新存储")
        void successWithCas() {
            stubStorageForDelta();
            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("收藏正文");
            dto.setTitle("标题");
            dto.setType("note");
            dto.setFileSize(1024L);

            FavoriteVO vo = service.create(USER_ID, dto);

            assertEquals(FAV_ID, vo.getId());
            assertEquals("收藏正文", vo.getContent());
            verify(sensitiveWordService).filter(anyString());
            verify(favoriteStorageMapper).casUpdateUsedBytes(USER_ID, 1024L, 1, 0);
        }

        @Test
        @DisplayName("敏感词拦截抛出 400")
        void blockedSensitive() {
            when(sensitiveWordService.filter(any())).thenReturn(
                    new SensitiveWordService.FilterResult("bad", false, true, false, List.of("违规词")));
            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("含违规词内容");

            CustomException ex = assertThrows(CustomException.class, () -> service.create(USER_ID, dto));
            assertEquals(400, ex.getCode());
            verify(favoriteMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("update 更新")
    class UpdateTests {

        @Test
        @DisplayName("成功更新内容、标签与文件大小")
        void successWithCas() {
            Favorite existing = ownedFavorite(100L);
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(existing);
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(100L, 1, 0));
            when(favoriteStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq(400L), eq(0), eq(0))).thenReturn(1);

            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("新内容");
            dto.setTags("[\"学习\",\"生活\"]");
            dto.setFileSize(500L);

            FavoriteVO vo = service.update(USER_ID, FAV_ID, dto);

            assertEquals("新内容", vo.getContent());
            assertEquals("[\"学习\",\"生活\"]", vo.getTags());
            verify(favoriteMapper).update(existing);
            verify(favoriteStorageMapper).casUpdateUsedBytes(USER_ID, 400L, 0, 0);
        }
    }

    @Nested
    @DisplayName("delete 删除")
    class DeleteTests {

        @Test
        @DisplayName("成功删除并扣减存储")
        void successWithStorageDelta() {
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(ownedFavorite(2048L));
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(2048L, 1, 0));
            when(favoriteStorageMapper.casUpdateUsedBytes(USER_ID, -2048L, -1, 0)).thenReturn(1);

            service.delete(USER_ID, FAV_ID);

            verify(favoriteMapper).deleteById(FAV_ID);
            verify(favoriteStorageMapper).casUpdateUsedBytes(USER_ID, -2048L, -1, 0);
        }
    }

    @Nested
    @DisplayName("adminDelete 管理员删除")
    class AdminDeleteTests {

        @Test
        @DisplayName("不存在返回 404")
        void notFound() {
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(null);

            CustomException ex = assertThrows(CustomException.class, () -> service.adminDelete(FAV_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("成功删除并更新所属用户存储")
        void success() {
            Favorite fav = ownedFavorite(512L);
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(fav);
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(512L, 1, 0));
            when(favoriteStorageMapper.casUpdateUsedBytes(USER_ID, -512L, -1, 0)).thenReturn(1);

            service.adminDelete(FAV_ID);

            verify(favoriteMapper).deleteById(FAV_ID);
            verify(favoriteStorageMapper).casUpdateUsedBytes(USER_ID, -512L, -1, 0);
        }
    }

    @Nested
    @DisplayName("getStorage 存储配额")
    class GetStorageTests {

        @Test
        @DisplayName("返回用量、配额、百分比与类型统计")
        void returnsUsageAndTypeCounts() {
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(1_073_741_824L, 2, 1));
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    Favorite.builder().type("note").build(),
                    Favorite.builder().type("image").build()
            ));

            FavoriteStorageVO vo = service.getStorage(USER_ID);

            assertEquals(1_073_741_824L, vo.getUsedBytes());
            assertEquals(FavoriteStorage.DEFAULT_QUOTA_BYTES, vo.getQuotaBytes());
            assertEquals(2, vo.getItemCount());
            assertTrue(vo.getUsedPercent() > 0);
            assertEquals(2, vo.getTypeCounts().get("all"));
            assertEquals(1, vo.getTypeCounts().get("note"));
            assertEquals(1, vo.getTypeCounts().get("image"));
        }
    }

    @Nested
    @DisplayName("listTags 标签列表")
    class ListTagsTests {

        @Test
        @DisplayName("已有预设标签时直接返回")
        void withExistingPresets() {
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L);
            when(favoriteTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    FavoriteTag.builder().id(1L).userId(USER_ID).name("工作").color("#f43f5e")
                            .sortOrder(0).preset(1).build(),
                    FavoriteTag.builder().id(2L).userId(USER_ID).name("自定义").color("#94a3b8")
                            .sortOrder(5).preset(0).build()
            ));
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    Favorite.builder().tags("[\"工作\"]").build()
            ));

            List<FavoriteTagVO> tags = service.listTags(USER_ID);

            assertEquals(2, tags.size());
            assertTrue(tags.get(0).getPreset());
            assertEquals(1, tags.get(0).getCount());
            assertEquals(0, tags.get(1).getCount());
            verify(favoriteTagMapper, never()).insert(any(FavoriteTag.class));
        }
    }

    @Nested
    @DisplayName("createTag 创建标签")
    class CreateTagTests {

        @Test
        @DisplayName("空白名称返回 400")
        void blankName() {
            SaveFavoriteTagDTO dto = new SaveFavoriteTagDTO();
            dto.setName("   ");

            CustomException ex = assertThrows(CustomException.class, () -> service.createTag(USER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("重复名称返回 400")
        void duplicateName() {
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class)))
                    .thenReturn(5L)
                    .thenReturn(1L);
            SaveFavoriteTagDTO dto = new SaveFavoriteTagDTO();
            dto.setName("工作");

            CustomException ex = assertThrows(CustomException.class, () -> service.createTag(USER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("成功创建自定义标签")
        void success() {
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class)))
                    .thenReturn(5L)
                    .thenReturn(0L);
            when(favoriteTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    FavoriteTag.builder().sortOrder(4).build()
            ));
            SaveFavoriteTagDTO dto = new SaveFavoriteTagDTO();
            dto.setName("项目");
            dto.setColor("#111111");

            FavoriteTagVO vo = service.createTag(USER_ID, dto);

            assertEquals(TAG_ID, vo.getId());
            assertEquals("项目", vo.getName());
            assertEquals("#111111", vo.getColor());
            assertEquals(5, vo.getSortOrder());
            assertFalse(vo.getPreset());
            assertEquals(0, vo.getCount());

            ArgumentCaptor<FavoriteTag> captor = ArgumentCaptor.forClass(FavoriteTag.class);
            verify(favoriteTagMapper).insert(captor.capture());
            assertEquals("项目", captor.getValue().getName());
            assertEquals(0, captor.getValue().getPreset());
        }
    }

    @Nested
    @DisplayName("deleteTag 删除标签")
    class DeleteTagTests {

        @Test
        @DisplayName("不存在返回 404")
        void notFound() {
            when(favoriteTagMapper.selectOneById(TAG_ID)).thenReturn(null);

            CustomException ex = assertThrows(CustomException.class, () -> service.deleteTag(USER_ID, TAG_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("预设标签不可删除")
        void presetForbidden() {
            when(favoriteTagMapper.selectOneById(TAG_ID)).thenReturn(
                    FavoriteTag.builder().id(TAG_ID).userId(USER_ID).preset(1).name("工作").build());

            CustomException ex = assertThrows(CustomException.class, () -> service.deleteTag(USER_ID, TAG_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("成功删除自定义标签")
        void success() {
            when(favoriteTagMapper.selectOneById(TAG_ID)).thenReturn(
                    FavoriteTag.builder().id(TAG_ID).userId(USER_ID).preset(0).name("项目").build());

            service.deleteTag(USER_ID, TAG_ID);

            verify(favoriteTagMapper).deleteById(TAG_ID);
        }

        @Test
        @DisplayName("非本人标签返回 404")
        void wrongOwner() {
            when(favoriteTagMapper.selectOneById(TAG_ID)).thenReturn(
                    FavoriteTag.builder().id(TAG_ID).userId(OTHER_ID).preset(0).name("项目").build());
            assertThrows(CustomException.class, () -> service.deleteTag(USER_ID, TAG_ID));
        }
    }

    @Nested
    @DisplayName("extended coverage")
    class ExtendedCoverage {

        @BeforeEach
        void enableReview() {
            when(adminReviewService.getIfAvailable()).thenReturn(reviewService);
        }

        @Test
        @DisplayName("list 返回收藏项")
        void listWithItems() {
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(100L, 1, 0));
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L);
            Favorite fav = ownedFavorite(100L);
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(fav));

            List<FavoriteVO> list = service.list(USER_ID);
            assertEquals(1, list.size());
            assertEquals(FAV_ID, list.get(0).getId());
        }

        @Test
        @DisplayName("create 敏感词 alert 入审")
        void createAlertEnqueueReview() {
            stubStorageForDelta();
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(ownedFavorite(0L));
            when(sensitiveWordService.filter(any())).thenReturn(
                    new SensitiveWordService.FilterResult("text", false, false, true, List.of("敏感")));
            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("含敏感词");
            dto.setTitle("标题");

            service.create(USER_ID, dto);
            verify(reviewService).createFromSensitiveHit(
                    eq(USER_ID), eq("favorite"), eq(String.valueOf(FAV_ID)), isNull(), anyString(),
                    eq("敏感"), eq("alert"));
        }

        @Test
        @DisplayName("update 文件大小不变不更新存储")
        void updateNoStorageDelta() {
            Favorite existing = ownedFavorite(100L);
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(existing);
            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setTitle("新标题");

            service.update(USER_ID, FAV_ID, dto);
            verify(favoriteStorageMapper, never()).casUpdateUsedBytes(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("adminDelete ownerId 为空仍删收藏")
        void adminDeleteNullOwner() {
            Favorite fav = Favorite.builder().id(FAV_ID).userId(null).content("x").fileSize(0L).build();
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(fav);
            service.adminDelete(FAV_ID);
            verify(favoriteMapper).deleteById(FAV_ID);
            verify(favoriteStorageMapper, never()).casUpdateUsedBytes(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("createTag 默认颜色")
        void createTagDefaultColor() {
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L, 0L);
            when(favoriteTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            SaveFavoriteTagDTO dto = new SaveFavoriteTagDTO();
            dto.setName("标签");
            FavoriteTagVO vo = service.createTag(USER_ID, dto);
            assertEquals("#94a3b8", vo.getColor());
        }

        @Test
        @DisplayName("getStorage 零配额百分比为 0")
        void getStorageZeroQuota() {
            FavoriteStorage zeroQuota = FavoriteStorage.builder()
                    .userId(USER_ID).usedBytes(100L).quotaBytes(0L).itemCount(1).version(0).build();
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(zeroQuota);
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            FavoriteStorageVO vo = service.getStorage(USER_ID);
            assertEquals(0.0, vo.getUsedPercent());
        }

        @Test
        @DisplayName("CAS 冲突后全量校正")
        void casFallbackRefresh() {
            when(favoriteMapper.selectOneById(FAV_ID)).thenReturn(ownedFavorite(512L));
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(storage(512L, 1, 0));
            when(favoriteStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq(-512L), eq(-1), eq(0))).thenReturn(0);
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            service.delete(USER_ID, FAV_ID);
            verify(favoriteStorageMapper).update(any(FavoriteStorage.class));
        }

        @Test
        @DisplayName("超配额自动扩容")
        void autoExpandCapacity() {
            long nearFull = FavoriteStorage.DEFAULT_QUOTA_BYTES - 100;
            when(favoriteStorageMapper.selectOneById(USER_ID))
                    .thenReturn(storage(nearFull, 0, 0))
                    .thenReturn(FavoriteStorage.builder()
                            .userId(USER_ID)
                            .usedBytes(nearFull)
                            .quotaBytes(FavoriteStorage.DEFAULT_QUOTA_BYTES + FavoriteStorage.EXPAND_STEP_BYTES)
                            .itemCount(0).version(1).build());
            when(favoriteStorageMapper.casExpandQuota(eq(USER_ID), anyLong(), eq(0))).thenReturn(1);
            when(favoriteStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq(2048L), eq(1), anyInt())).thenReturn(1);

            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("大文件收藏");
            dto.setFileSize(2048L);
            service.create(USER_ID, dto);
            verify(favoriteStorageMapper).casExpandQuota(eq(USER_ID), anyLong(), eq(0));
        }

        @Test
        @DisplayName("达 60GB 上限拒绝")
        void maxQuotaRejected() {
            FavoriteStorage maxed = FavoriteStorage.builder()
                    .userId(USER_ID)
                    .usedBytes(FavoriteStorage.MAX_QUOTA_BYTES)
                    .quotaBytes(FavoriteStorage.MAX_QUOTA_BYTES)
                    .itemCount(1).version(0).build();
            when(favoriteStorageMapper.selectOneById(USER_ID)).thenReturn(maxed);

            SaveFavoriteDTO dto = new SaveFavoriteDTO();
            dto.setContent("超限");
            dto.setFileSize(1024L);
            CustomException ex = assertThrows(CustomException.class, () -> service.create(USER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("listTags 逗号分隔标签计数")
        void listTagsCommaTags() {
            when(favoriteTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(favoriteTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    FavoriteTag.builder().id(1L).userId(USER_ID).name("自定义").color("#111").sortOrder(0).preset(0).build()
            ));
            when(favoriteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    Favorite.builder().tags("工作,学习").build()
            ));

            List<FavoriteTagVO> tags = service.listTags(USER_ID);
            assertEquals(1, tags.size());
        }
    }
}
