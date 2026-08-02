package com.linkx.server.service.impl;

import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.controller.dto.CreateDriveFolderDTO;
import com.linkx.server.controller.dto.CreateDriveShareDTO;
import com.linkx.server.controller.dto.DriveBatchDTO;
import com.linkx.server.controller.dto.UpdateDriveItemDTO;
import com.linkx.server.controller.vo.DriveItemVO;
import com.linkx.server.controller.vo.DriveShareVO;
import com.linkx.server.controller.vo.DriveStorageVO;
import com.linkx.server.entity.CloudActivity;
import com.linkx.server.entity.CloudFile;
import com.linkx.server.entity.CloudFileTag;
import com.linkx.server.entity.CloudFolder;
import com.linkx.server.entity.CloudShare;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.UserStorage;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.CloudActivityMapper;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.CloudFileTagMapper;
import com.linkx.server.mapper.CloudFolderMapper;
import com.linkx.server.mapper.CloudShareMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.UserStorageMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CloudDriveServiceImpl 网盘")
class CloudDriveServiceImplTest {

    private static final long USER_ID = 42L;

    @Mock UserStorageMapper userStorageMapper;
    @Mock CloudFolderMapper cloudFolderMapper;
    @Mock CloudFileMapper cloudFileMapper;
    @Mock CloudFileTagMapper cloudFileTagMapper;
    @Mock CloudShareMapper cloudShareMapper;
    @Mock CloudActivityMapper cloudActivityMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private CloudDriveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudDriveServiceImpl(
                userStorageMapper,
                cloudFolderMapper,
                cloudFileMapper,
                cloudFileTagMapper,
                cloudShareMapper,
                cloudActivityMapper,
                sysUserMapper,
                fileStorageService,
                mediaUrlService,
                objectKeyOwnershipService,
                stringRedisTemplate
        );
    }

    private UserStorage storage(long used, long quota, int version) {
        return UserStorage.builder()
                .userId(USER_ID)
                .usedBytes(used)
                .quotaBytes(quota)
                .fileCount(0)
                .version(version)
                .build();
    }

    private CloudFolder folder(long id, Long parentId, String name, String path) {
        Date now = new Date();
        return CloudFolder.builder()
                .id(id)
                .userId(USER_ID)
                .parentId(parentId)
                .name(name)
                .path(path)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private CloudFile file(long id, Long folderId, String name, String key) {
        Date now = new Date();
        return CloudFile.builder()
                .id(id)
                .userId(USER_ID)
                .folderId(folderId)
                .name(name)
                .fileName(name)
                .fileSize(100L)
                .fileKey(key)
                .contentType("text/plain")
                .ext("txt")
                .category("document")
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private SysUser me() {
        return SysUser.builder().id(USER_ID).nickname("Alice").avatar("av.png").build();
    }

    private void stubEnsureStorage(UserStorage s) {
        when(userStorageMapper.selectOneById(USER_ID)).thenReturn(s);
    }

    @Nested
    @DisplayName("存储配额")
    class StorageQuota {
        @Test
        @DisplayName("查询存储与自动初始化")
        void getStorage_and_init() {
            UserStorage existing = storage(1024, UserStorage.DEFAULT_QUOTA_BYTES, 0);
            stubEnsureStorage(existing);
            DriveStorageVO vo = service.getStorage(USER_ID);
            assertEquals(1024, vo.getUsedBytes());
            assertEquals(UserStorage.DEFAULT_QUOTA_BYTES, vo.getQuotaBytes());

            when(userStorageMapper.selectOneById(99L)).thenReturn(null, storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            DriveStorageVO init = service.getStorage(99L);
            assertNotNull(init);
            verify(userStorageMapper).insert(any(UserStorage.class));
        }

        @Test
        @DisplayName("扩容成功与上限/冲突")
        void expandStorage() {
            UserStorage s = storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 1);
            stubEnsureStorage(s);
            when(userStorageMapper.casExpandQuota(eq(USER_ID), anyLong(), eq(1))).thenReturn(1);

            DriveStorageVO expanded = service.expandStorage(USER_ID);
            assertTrue(expanded.getQuotaBytes() > UserStorage.DEFAULT_QUOTA_BYTES);

            UserStorage maxed = storage(0, UserStorage.MAX_QUOTA_BYTES, 2);
            stubEnsureStorage(maxed);
            assertThrows(CustomException.class, () -> service.expandStorage(USER_ID));

            UserStorage conflict = storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 3);
            stubEnsureStorage(conflict);
            when(userStorageMapper.casExpandQuota(eq(USER_ID), anyLong(), eq(3))).thenReturn(0);
            assertThrows(CustomException.class, () -> service.expandStorage(USER_ID));
        }
    }

    @Nested
    @DisplayName("列表与面包屑")
    class ListAndBreadcrumb {
        @Test
        @DisplayName("根目录列表含文件夹统计与关键字搜索")
        void listItems_root_and_search() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            CloudFolder rootFolder = folder(10L, null, "Docs", "/Docs");
            CloudFile rootFile = file(20L, null, "readme.txt", "k/readme.txt");

            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(rootFolder))
                    .thenReturn(List.of(rootFolder));
            when(cloudFileMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(rootFile))
                    .thenReturn(List.of(rootFile));
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");
            when(mediaUrlService.resolveFile("k/readme.txt")).thenReturn("https://cdn/readme.txt");
            when(cloudFileTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            List<DriveItemVO> items = service.listItems(USER_ID, null, null);
            assertEquals(2, items.size());
            assertEquals("folder", items.get(0).getKind());

            List<DriveItemVO> filtered = service.listItems(USER_ID, null, "readme");
            assertTrue(filtered.stream().anyMatch(i -> "file".equals(i.getKind())));
        }

        @Test
        @DisplayName("子目录列表与不存在文件夹")
        void listItems_inFolder() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            CloudFolder parent = folder(5L, null, "P", "/P");
            when(cloudFolderMapper.selectOneById(5L)).thenReturn(parent);
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(cloudFileMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");
            when(cloudFileTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            assertTrue(service.listItems(USER_ID, 5L, null).isEmpty());
            when(cloudFolderMapper.selectOneById(999L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.listItems(USER_ID, 999L, null));
        }

        @Test
        @DisplayName("面包屑链")
        void breadcrumb() {
            CloudFolder child = folder(2L, 1L, "B", "/A/B");
            CloudFolder parent = folder(1L, null, "A", "/A");
            when(cloudFolderMapper.selectOneById(2L)).thenReturn(child);
            when(cloudFolderMapper.selectOneById(1L)).thenReturn(parent);

            List<DriveItemVO> chain = service.breadcrumb(USER_ID, 2L);
            assertEquals(2, chain.size());
            assertEquals("A", chain.get(0).getName());
            assertEquals("B", chain.get(1).getName());
        }
    }

    @Nested
    @DisplayName("文件夹")
    class Folders {
        @Test
        @DisplayName("创建根目录与子文件夹")
        void createFolder() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(cloudFolderMapper.insert(any(CloudFolder.class))).thenAnswer(inv -> {
                CloudFolder f = inv.getArgument(0);
                f.setId(100L);
                return 1;
            });
            when(cloudFolderMapper.selectOneById(100L)).thenReturn(folder(100L, null, "New", "/New"));
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");

            CreateDriveFolderDTO dto = new CreateDriveFolderDTO();
            dto.setName("  New  ");
            DriveItemVO created = service.createFolder(USER_ID, dto);
            assertEquals("New", created.getName());
            verify(cloudActivityMapper).insert(any(CloudActivity.class));
        }

        @Test
        @DisplayName("重名文件夹拒绝")
        void duplicateFolder() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(folder(1L, null, "dup", "/dup")));

            CreateDriveFolderDTO dto = new CreateDriveFolderDTO();
            dto.setName("dup");
            assertThrows(CustomException.class, () -> service.createFolder(USER_ID, dto));
        }

        @Test
        @DisplayName("重命名/移动/禁止移到自身或子目录")
        void updateFolder() {
            CloudFolder f = folder(10L, null, "Old", "/Old");
            CloudFolder child = folder(11L, 10L, "Child", "/Old/Child");
            when(cloudFolderMapper.selectOneById(10L)).thenReturn(f);
            when(cloudFolderMapper.selectOneById(11L)).thenReturn(child);
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");

            UpdateDriveItemDTO rename = new UpdateDriveItemDTO();
            rename.setName("Renamed");
            DriveItemVO renamed = service.updateFolder(USER_ID, 10L, rename);
            assertEquals("Renamed", renamed.getName());
            verify(cloudFolderMapper).update(f);

            UpdateDriveItemDTO moveSelf = new UpdateDriveItemDTO();
            moveSelf.setFolderId("10");
            assertThrows(CustomException.class, () -> service.updateFolder(USER_ID, 10L, moveSelf));

            UpdateDriveItemDTO moveToChild = new UpdateDriveItemDTO();
            moveToChild.setFolderId("11");
            assertThrows(CustomException.class, () -> service.updateFolder(USER_ID, 10L, moveToChild));
        }

        @Test
        @DisplayName("递归删除文件夹")
        void deleteFolderRecursive() {
            CloudFolder parent = folder(1L, null, "P", "/P");
            CloudFolder child = folder(2L, 1L, "C", "/P/C");
            CloudFile inChild = file(3L, 2L, "a.txt", "k/a.txt");

            when(cloudFolderMapper.selectOneById(1L)).thenReturn(parent);
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(child))
                    .thenReturn(List.of());
            when(cloudFileMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(inChild))
                    .thenReturn(List.of());
            when(cloudFileMapper.selectOneById(3L)).thenReturn(inChild);
            stubEnsureStorage(storage(100, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            when(userStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq(-100L), eq(-1), eq(0))).thenReturn(1);

            service.deleteFolder(USER_ID, 1L);
            verify(cloudFolderMapper).deleteById(1L);
            verify(cloudFileMapper).deleteById(3L);
            verify(fileStorageService).deleteFile("k/a.txt");
        }
    }

    @Nested
    @DisplayName("文件")
    class Files {
        @Test
        @DisplayName("上传校验与成功")
        void upload() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            MockMultipartFile empty = new MockMultipartFile("f", "a.txt", "text/plain", new byte[0]);
            assertThrows(CustomException.class, () -> service.upload(USER_ID, null, empty));

            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile ok = new MockMultipartFile("f", "note.txt", "text/plain", content);
            when(fileStorageService.uploadFile(ok)).thenReturn("2026/08/02/uuid.txt");
            when(cloudFileMapper.insert(any(CloudFile.class))).thenAnswer(inv -> {
                CloudFile cf = inv.getArgument(0);
                cf.setId(50L);
                return 1;
            });
            when(cloudFileMapper.selectOneById(50L)).thenReturn(file(50L, null, "note.txt", "2026/08/02/uuid.txt"));
            when(userStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq((long) content.length), eq(1), eq(0))).thenReturn(1);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");
            when(mediaUrlService.resolveFile("2026/08/02/uuid.txt")).thenReturn("https://cdn/note.txt");

            DriveItemVO uploaded = service.upload(USER_ID, null, ok);
            assertEquals("file", uploaded.getKind());
            verify(objectKeyOwnershipService).claim(USER_ID, "2026/08/02/uuid.txt");
        }

        @Test
        @DisplayName("上传 CAS 冲突触发异步删对象")
        void upload_casConflict() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            MockMultipartFile ok = new MockMultipartFile("f", "note.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
            when(fileStorageService.uploadFile(ok)).thenReturn("orphan/key.txt");
            when(cloudFileMapper.insert(any(CloudFile.class))).thenAnswer(inv -> {
                CloudFile cf = inv.getArgument(0);
                cf.setId(51L);
                return 1;
            });
            when(cloudFileMapper.selectOneById(51L)).thenReturn(file(51L, null, "note.txt", "orphan/key.txt"));
            when(userStorageMapper.casUpdateUsedBytes(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(0);

            assertThrows(CustomException.class, () -> service.upload(USER_ID, null, ok));
            verify(fileStorageService).deleteFileAsync("orphan/key.txt");
        }

        @Test
        @DisplayName("获取/更新/删除文件")
        void get_update_delete() {
            CloudFile cf = file(60L, null, "doc.txt", "k/doc.txt");
            when(cloudFileMapper.selectOneById(60L)).thenReturn(cf);
            when(cloudFileTagMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(CloudFileTag.builder().fileId(60L).tagName("work").build()));
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");
            when(mediaUrlService.resolveFile("k/doc.txt")).thenReturn("https://cdn/doc.txt");

            DriveItemVO got = service.getFile(USER_ID, 60L);
            assertEquals("doc.txt", got.getName());
            assertEquals(List.of("work"), got.getTags());

            UpdateDriveItemDTO upd = new UpdateDriveItemDTO();
            upd.setName("renamed.txt");
            upd.setDescription("desc");
            upd.setFolderId("");
            DriveItemVO updated = service.updateFile(USER_ID, 60L, upd);
            assertEquals("renamed.txt", updated.getName());
            verify(cloudFileMapper).update(cf);

            stubEnsureStorage(storage(100, UserStorage.DEFAULT_QUOTA_BYTES, 1));
            when(userStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq(-100L), eq(-1), eq(1))).thenReturn(1);
            service.deleteFile(USER_ID, 60L);
            verify(cloudFileMapper).deleteById(60L);
        }

        @Test
        @DisplayName("打开文件内容")
        void openFileContent() {
            CloudFile cf = file(70L, null, "dl.txt", "k/dl.txt");
            when(cloudFileMapper.selectOneById(70L)).thenReturn(cf);
            FileStorageService.StoredObject obj = new FileStorageService.StoredObject(
                    new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "text/plain", 1, "k/dl.txt");
            when(fileStorageService.openObject("k/dl.txt")).thenReturn(obj);

            FileStorageService.StoredObject opened = service.openFileContent(USER_ID, 70L);
            assertEquals("text/plain", opened.contentType());
            verify(cloudActivityMapper).insert(any(CloudActivity.class));
        }
    }

    @Nested
    @DisplayName("批量与标签")
    class BatchAndTags {
        @Test
        @DisplayName("批量删除/移动与空批量校验")
        void batchOperations() {
            DriveBatchDTO empty = new DriveBatchDTO();
            assertThrows(CustomException.class, () -> service.batchDelete(USER_ID, empty));

            CloudFile cf = file(80L, null, "b.txt", "k/b.txt");
            when(cloudFileMapper.selectOneById(80L)).thenReturn(cf);
            stubEnsureStorage(storage(100, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            when(userStorageMapper.casUpdateUsedBytes(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(1);

            DriveBatchDTO del = new DriveBatchDTO();
            del.setKind("file");
            del.setIds(List.of("80"));
            service.batchDelete(USER_ID, del);

            CloudFolder f = folder(90L, null, "M", "/M");
            when(cloudFolderMapper.selectOneById(90L)).thenReturn(f);
            when(cloudFolderMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(cloudFileMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");

            DriveBatchDTO move = new DriveBatchDTO();
            move.setTargetFolderId("");
            DriveBatchDTO.DriveBatchItem item = new DriveBatchDTO.DriveBatchItem();
            item.setKind("folder");
            item.setId("90");
            move.setItems(List.of(item));
            service.batchMove(USER_ID, move);
            verify(cloudFolderMapper, atLeastOnce()).update(f);
        }

        @Test
        @DisplayName("添加/移除标签")
        void tags() {
            CloudFile cf = file(81L, null, "t.txt", "k/t.txt");
            when(cloudFileMapper.selectOneById(81L)).thenReturn(cf);
            when(cloudFileTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(cloudFileTagMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(CloudFileTag.builder().fileId(81L).tagName("vip").build()));

            List<String> added = service.addTag(USER_ID, 81L, "  vip ");
            assertEquals(List.of("vip"), added);

            List<String> removed = service.removeTag(USER_ID, 81L, "vip");
            assertEquals(List.of("vip"), removed);
            verify(cloudFileTagMapper).deleteByQuery(any(QueryWrapper.class));

            assertThrows(CustomException.class, () -> service.addTag(USER_ID, 81L, "  "));
        }
    }

    @Nested
    @DisplayName("活动与分享")
    class ActivityAndShare {
        @Test
        @DisplayName("活动列表")
        void listActivities() {
            CloudActivity act = CloudActivity.builder()
                    .id(1L)
                    .userId(USER_ID)
                    .targetType(CloudActivity.TARGET_FILE)
                    .targetId(60L)
                    .targetName("a")
                    .action(CloudActivity.ACTION_UPLOAD)
                    .detail("上传")
                    .createTime(new Date())
                    .build();
            when(cloudActivityMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(act));

            assertEquals(1, service.listActivities(USER_ID, null, 10).size());
            assertEquals(1, service.listActivities(USER_ID, 60L, 200).size());
        }

        @Test
        @DisplayName("创建/撤销分享")
        void create_and_revokeShare() {
            CloudFile cf = file(100L, null, "share.txt", "k/share.txt");
            when(cloudFileMapper.selectOneById(100L)).thenReturn(cf);
            when(cloudShareMapper.insert(any(CloudShare.class))).thenAnswer(inv -> {
                CloudShare s = inv.getArgument(0);
                s.setId(200L);
                return 1;
            });

            CreateDriveShareDTO dto = new CreateDriveShareDTO();
            dto.setShareType("file");
            dto.setTargetId("100");
            dto.setPassword("secret");
            dto.setExpireHours(24);
            dto.setMaxDownloads(5);
            DriveShareVO share = service.createShare(USER_ID, dto);
            assertTrue(share.isHasPassword());
            assertNotNull(share.getToken());

            CloudFolder fld = folder(101L, null, "ShareDir", "/ShareDir");
            when(cloudFolderMapper.selectOneById(101L)).thenReturn(fld);
            CreateDriveShareDTO folderDto = new CreateDriveShareDTO();
            folderDto.setShareType("folder");
            folderDto.setTargetId("101");
            assertNotNull(service.createShare(USER_ID, folderDto));

            CreateDriveShareDTO bad = new CreateDriveShareDTO();
            bad.setShareType("link");
            bad.setTargetId("1");
            assertThrows(CustomException.class, () -> service.createShare(USER_ID, bad));

            CloudShare existing = CloudShare.builder().id(200L).userId(USER_ID).status(1).build();
            when(cloudShareMapper.selectOneById(200L)).thenReturn(existing);
            service.revokeShare(USER_ID, 200L);
            assertEquals(0, existing.getStatus());

            when(cloudShareMapper.selectOneById(201L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.revokeShare(USER_ID, 201L));
        }

        @Test
        @DisplayName("公开分享：密码/过期/下载")
        void publicShare() {
            CloudShare share = CloudShare.builder()
                    .id(300L)
                    .userId(USER_ID)
                    .shareType(CloudShare.TYPE_FILE)
                    .targetId(100L)
                    .token("tok123")
                    .passwordHash(PasswordEncoderHolder.encode("1234"))
                    .status(1)
                    .downloadCount(0)
                    .build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(share);
            when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

            assertThrows(CustomException.class, () -> service.getPublicShare("tok123", null));

            when(valueOps.increment(anyString())).thenReturn(1L);
            assertThrows(CustomException.class, () -> service.getPublicShare("tok123", "wrong"));

            when(stringRedisTemplate.delete(anyString())).thenReturn(true);
            CloudFile cf = file(100L, null, "pub.txt", "k/pub.txt");
            when(cloudFileMapper.selectOneById(100L)).thenReturn(cf);
            when(mediaUrlService.resolveShare("k/pub.txt")).thenReturn("https://signed/pub");

            DriveShareVO pub = service.getPublicShare("tok123", "1234");
            assertEquals("pub.txt", pub.getTargetName());

            when(cloudShareMapper.incrementDownloadCount(300L)).thenReturn(1);
            String url = service.downloadPublicShare("tok123", "1234");
            assertEquals("https://signed/pub", url);

            CloudShare expired = CloudShare.builder()
                    .id(301L).userId(USER_ID).shareType(CloudShare.TYPE_FILE)
                    .targetId(100L).token("exp").status(1)
                    .expireAt(new Date(System.currentTimeMillis() - 3600_000))
                    .build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(expired);
            assertThrows(CustomException.class, () -> service.getPublicShare("exp", null));

            CloudShare folderShare = CloudShare.builder()
                    .id(302L).userId(USER_ID).shareType(CloudShare.TYPE_FOLDER)
                    .targetId(101L).token("fld").status(1).build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(folderShare);
            assertThrows(CustomException.class, () -> service.downloadPublicShare("fld", null));

            when(cloudShareMapper.incrementDownloadCount(300L)).thenReturn(0);
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(share);
            assertThrows(CustomException.class, () -> service.downloadPublicShare("tok123", "1234"));
        }

        @Test
        @DisplayName("分享内容流与下载次数用尽")
        void openShareContent() {
            CloudShare share = CloudShare.builder()
                    .id(400L)
                    .userId(USER_ID)
                    .shareType(CloudShare.TYPE_FILE)
                    .targetId(100L)
                    .token("stream")
                    .status(1)
                    .build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(share);
            when(cloudShareMapper.incrementDownloadCount(400L)).thenReturn(1);
            CloudFile cf = file(100L, null, "s.txt", "k/s.txt");
            when(cloudFileMapper.selectOneById(100L)).thenReturn(cf);
            FileStorageService.StoredObject obj = new FileStorageService.StoredObject(
                    new ByteArrayInputStream("d".getBytes(StandardCharsets.UTF_8)), "text/plain", 1, "k/s.txt");
            when(fileStorageService.openObject("k/s.txt")).thenReturn(obj);

            assertNotNull(service.openShareContent("stream", null));
        }
    }

    @Nested
    @DisplayName("extended coverage")
    class ExtendedCoverage {
        @Test
        @DisplayName("上传非法参数转 400")
        void uploadIllegalArgument() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            MockMultipartFile bad = new MockMultipartFile("f", "note.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
            when(fileStorageService.uploadFile(bad)).thenThrow(new IllegalArgumentException("不允许的文件类型"));
            assertThrows(CustomException.class, () -> service.upload(USER_ID, null, bad));
        }

        @Test
        @DisplayName("上传运行时异常转 500")
        void uploadRuntimeFailure() {
            stubEnsureStorage(storage(0, UserStorage.DEFAULT_QUOTA_BYTES, 0));
            MockMultipartFile bad = new MockMultipartFile("f", "note.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
            when(fileStorageService.uploadFile(bad)).thenThrow(new RuntimeException("minio down"));
            assertThrows(CustomException.class, () -> service.upload(USER_ID, null, bad));
        }

        @Test
        @DisplayName("上传超配额自动扩容")
        void uploadAutoExpand() {
            long nearFull = UserStorage.DEFAULT_QUOTA_BYTES - 10;
            UserStorage s = storage(nearFull, UserStorage.DEFAULT_QUOTA_BYTES, 0);
            stubEnsureStorage(s);
            when(userStorageMapper.casExpandQuota(eq(USER_ID), anyLong(), eq(0))).thenReturn(1);
            MockMultipartFile ok = new MockMultipartFile("f", "big.txt", "text/plain", new byte[20]);
            when(fileStorageService.uploadFile(ok)).thenReturn("2026/big.txt");
            when(cloudFileMapper.insert(any(CloudFile.class))).thenAnswer(inv -> {
                CloudFile cf = inv.getArgument(0);
                cf.setId(55L);
                return 1;
            });
            when(cloudFileMapper.selectOneById(55L)).thenReturn(file(55L, null, "big.txt", "2026/big.txt"));
            when(userStorageMapper.casUpdateUsedBytes(eq(USER_ID), eq(20L), eq(1), eq(0))).thenReturn(1);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(me());
            when(mediaUrlService.resolve("av.png")).thenReturn("https://cdn/av.png");
            when(mediaUrlService.resolveFile("2026/big.txt")).thenReturn("https://cdn/big.txt");

            DriveItemVO uploaded = service.upload(USER_ID, null, ok);
            assertEquals("big.txt", uploaded.getName());
            verify(userStorageMapper).casExpandQuota(eq(USER_ID), anyLong(), eq(0));
        }

        @Test
        @DisplayName("上传达 60GB 上限")
        void uploadMaxQuota() {
            UserStorage maxed = storage(UserStorage.MAX_QUOTA_BYTES, UserStorage.MAX_QUOTA_BYTES, 0);
            stubEnsureStorage(maxed);
            MockMultipartFile ok = new MockMultipartFile("f", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
            assertThrows(CustomException.class, () -> service.upload(USER_ID, null, ok));
        }

        @Test
        @DisplayName("getFile 不存在")
        void getFileMissing() {
            when(cloudFileMapper.selectOneById(999L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.getFile(USER_ID, 999L));
        }

        @Test
        @DisplayName("addTag 重复标签不重复插入")
        void addTagDuplicate() {
            CloudFile cf = file(82L, null, "t.txt", "k/t.txt");
            when(cloudFileMapper.selectOneById(82L)).thenReturn(cf);
            when(cloudFileTagMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(cloudFileTagMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(CloudFileTag.builder().fileId(82L).tagName("vip").build()));
            List<String> tags = service.addTag(USER_ID, 82L, "vip");
            assertEquals(List.of("vip"), tags);
            verify(cloudFileTagMapper, never()).insert(any());
        }

        @Test
        @DisplayName("分享密码错误达上限锁定")
        void sharePasswordLockout() {
            CloudShare share = CloudShare.builder()
                    .id(500L).userId(USER_ID).shareType(CloudShare.TYPE_FILE)
                    .targetId(100L).token("locktok").status(1)
                    .passwordHash(PasswordEncoderHolder.encode("1234")).build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(share);
            when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment(anyString())).thenReturn(5L);

            assertThrows(CustomException.class, () -> service.getPublicShare("locktok", "wrong"));
            verify(valueOps).set(startsWith("linkx:share:pwd:lock:"), eq("1"), any());
        }

        @Test
        @DisplayName("分享不存在")
        void shareNotFound() {
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class, () -> service.getPublicShare("missing", null));
        }

        @Test
        @DisplayName("openShareContent 文件夹分享拒绝")
        void openShareFolderRejected() {
            CloudShare folderShare = CloudShare.builder()
                    .id(600L).userId(USER_ID).shareType(CloudShare.TYPE_FOLDER)
                    .targetId(101L).token("fld2").status(1).build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(folderShare);
            assertThrows(CustomException.class, () -> service.openShareContent("fld2", null));
        }

        @Test
        @DisplayName("downloadPublicShare 文件不存在")
        void downloadMissingFile() {
            CloudShare share = CloudShare.builder()
                    .id(700L).userId(USER_ID).shareType(CloudShare.TYPE_FILE)
                    .targetId(100L).token("nofile").status(1).build();
            when(cloudShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(share);
            when(cloudShareMapper.incrementDownloadCount(700L)).thenReturn(1);
            when(cloudFileMapper.selectOneById(100L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.downloadPublicShare("nofile", null));
        }
    }
}
