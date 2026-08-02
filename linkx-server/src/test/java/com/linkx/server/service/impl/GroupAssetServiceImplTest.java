package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CreateGroupAssetDTO;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.AdminReviewService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupAssetServiceImpl 群资源")
class GroupAssetServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long CONV_ID = 100L;

    @Mock GroupAssetMapper groupAssetMapper;
    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock ObjectProvider<AdminReviewService> adminReviewServiceProvider;

    private GroupAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GroupAssetServiceImpl(
                groupAssetMapper,
                conversationMapper,
                memberMapper,
                sysUserMapper,
                fileStorageService,
                mediaUrlService,
                objectKeyOwnershipService,
                sensitiveWordService,
                adminReviewServiceProvider
        );
    }

    private ImConversation group(Long ownerId) {
        return ImConversation.builder()
                .id(CONV_ID)
                .type(ImConversation.TYPE_GROUP)
                .ownerId(ownerId)
                .name("G1")
                .build();
    }

    private ImConversationMember member(String role) {
        return ImConversationMember.builder()
                .conversationId(CONV_ID)
                .userId(USER_ID)
                .role(role)
                .build();
    }

    private void stubMember() {
        when(conversationMapper.selectOneById(CONV_ID)).thenReturn(group(999L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member(ImConversationMember.ROLE_MEMBER));
    }

    private GroupAsset asset(long id, String type, Long uploaderId) {
        return GroupAsset.builder()
                .id(id)
                .conversationId(CONV_ID)
                .uploaderId(uploaderId)
                .type(type)
                .title("t")
                .fileName("f.txt")
                .fileKey("k/f.txt")
                .fileSize(10L)
                .downloadCount(0)
                .createTime(new Date())
                .build();
    }

    @Nested
    @DisplayName("列表")
    class ListAssets {
        @Test
        @DisplayName("按类型分页列表")
        void list_ok() {
            stubMember();
            GroupAsset a = asset(1L, GroupAsset.TYPE_FILE, USER_ID);
            when(groupAssetMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(a));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(SysUser.builder().id(USER_ID).nickname("Bob").build()));
            when(mediaUrlService.resolveFile("k/f.txt")).thenReturn("https://cdn/f.txt");

            assertEquals(1, service.list(USER_ID, CONV_ID, "file").size());
            assertEquals(1, service.list(USER_ID, CONV_ID, null, 50).size());
        }

        @Test
        @DisplayName("非群成员拒绝")
        void list_forbidden() {
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(group(1L));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class, () -> service.list(USER_ID, CONV_ID, null));
        }
    }

    @Nested
    @DisplayName("创建")
    class CreateAsset {
        @Test
        @DisplayName("文件资源需 claim 过的 key")
        void create_file() {
            stubMember();
            when(sensitiveWordService.filter(anyString()))
                    .thenReturn(new SensitiveWordService.FilterResult("title", false, false, false, List.of()));
            when(groupAssetMapper.insert(any(GroupAsset.class))).thenAnswer(inv -> {
                GroupAsset g = inv.getArgument(0);
                g.setId(10L);
                return 1;
            });
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(SysUser.builder().id(USER_ID).nickname("U").build());
            when(mediaUrlService.resolveFile("owned/key.txt")).thenReturn("https://cdn/x");

            CreateGroupAssetDTO dto = new CreateGroupAssetDTO();
            dto.setType("file");
            dto.setTitle("title");
            dto.setFileKey("owned/key.txt");
            dto.setFileName("doc.txt");
            dto.setFileSize(99L);

            assertNotNull(service.create(USER_ID, CONV_ID, dto));
            verify(objectKeyOwnershipService).assertOwned(USER_ID, "owned/key.txt");
        }

        @Test
        @DisplayName("精华需管理员且内容非空")
        void create_essence() {
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(group(USER_ID));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(member(ImConversationMember.ROLE_ADMIN));
            when(sensitiveWordService.filter(anyString()))
                    .thenReturn(new SensitiveWordService.FilterResult("精华", false, false, false, List.of()));
            when(groupAssetMapper.insert(any(GroupAsset.class))).thenAnswer(inv -> {
                GroupAsset g = inv.getArgument(0);
                g.setId(11L);
                return 1;
            });
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(SysUser.builder().id(USER_ID).nickname("Owner").build());
            when(mediaUrlService.resolveFile(isNull())).thenReturn(null);

            CreateGroupAssetDTO dto = new CreateGroupAssetDTO();
            dto.setType("essence");
            dto.setTitle("tip");
            assertNotNull(service.create(USER_ID, CONV_ID, dto));

            CreateGroupAssetDTO empty = new CreateGroupAssetDTO();
            empty.setType("essence");
            assertThrows(CustomException.class, () -> service.create(USER_ID, CONV_ID, empty));
        }

        @Test
        @DisplayName("敏感词拦截")
        void create_blocked() {
            stubMember();
            when(sensitiveWordService.filter(anyString()))
                    .thenReturn(new SensitiveWordService.FilterResult("bad", false, true, false, List.of("bad")));
            when(adminReviewServiceProvider.getIfAvailable()).thenReturn(null);

            CreateGroupAssetDTO dto = new CreateGroupAssetDTO();
            dto.setType("file");
            dto.setTitle("bad title");
            dto.setFileKey("k/x.txt");
            assertThrows(CustomException.class, () -> service.create(USER_ID, CONV_ID, dto));
        }

        @Test
        @DisplayName("缺少 fileKey")
        void create_missingKey() {
            stubMember();
            CreateGroupAssetDTO dto = new CreateGroupAssetDTO();
            dto.setType("file");
            assertThrows(CustomException.class, () -> service.create(USER_ID, CONV_ID, dto));
        }
    }

    @Nested
    @DisplayName("上传与删除")
    class UploadAndDelete {
        @Test
        @DisplayName("上传 file 类型")
        void upload_file() {
            stubMember();
            MockMultipartFile file = new MockMultipartFile(
                    "f", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
            when(fileStorageService.uploadFile(eq(file), isNull())).thenReturn("2026/note.txt");
            when(sensitiveWordService.filter(anyString()))
                    .thenReturn(new SensitiveWordService.FilterResult("note", false, false, false, List.of()));
            when(groupAssetMapper.insert(any(GroupAsset.class))).thenAnswer(inv -> {
                GroupAsset g = inv.getArgument(0);
                g.setId(20L);
                return 1;
            });
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(SysUser.builder().id(USER_ID).nickname("U").build());
            when(mediaUrlService.resolveFile("2026/note.txt")).thenReturn("https://cdn/note");

            assertNotNull(service.upload(USER_ID, CONV_ID, "file", file, null));
            verify(objectKeyOwnershipService).claim(USER_ID, "2026/note.txt");
        }

        @Test
        @DisplayName("上传 image 需有效图片头")
        void upload_image_invalid() {
            stubMember();
            MockMultipartFile bad = new MockMultipartFile("f", "a.png", "image/png", "not-png".getBytes(StandardCharsets.UTF_8));
            assertThrows(CustomException.class, () -> service.upload(USER_ID, CONV_ID, "image", bad, "相册"));
        }

        @Test
        @DisplayName("删除权限：上传者/群主/管理员")
        void delete_permissions() {
            stubMember();
            GroupAsset mine = asset(30L, GroupAsset.TYPE_FILE, USER_ID);
            when(groupAssetMapper.selectOneById(30L)).thenReturn(mine);
            service.delete(USER_ID, CONV_ID, 30L);
            verify(groupAssetMapper).deleteById(30L);

            GroupAsset others = asset(31L, GroupAsset.TYPE_FILE, 888L);
            when(groupAssetMapper.selectOneById(31L)).thenReturn(others);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(group(999L));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member(ImConversationMember.ROLE_MEMBER));
            assertThrows(CustomException.class, () -> service.delete(USER_ID, CONV_ID, 31L));

            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member(ImConversationMember.ROLE_ADMIN));
            service.delete(USER_ID, CONV_ID, 31L);
        }

        @Test
        @DisplayName("管理员删除")
        void adminDelete() {
            when(groupAssetMapper.selectOneById(40L)).thenReturn(asset(40L, GroupAsset.TYPE_FILE, 1L));
            service.adminDelete(40L);
            verify(groupAssetMapper).deleteById(40L);
            when(groupAssetMapper.selectOneById(41L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.adminDelete(41L));
        }
    }

    @Nested
    @DisplayName("内容访问")
    class ContentAccess {
        @Test
        @DisplayName("打开资源流与文件名")
        void open_and_filename() {
            stubMember();
            GroupAsset a = asset(50L, GroupAsset.TYPE_FILE, USER_ID);
            when(groupAssetMapper.selectOneById(50L)).thenReturn(a);
            FileStorageService.StoredObject obj = new FileStorageService.StoredObject(
                    new java.io.ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                    "text/plain", 1, "k/f.txt");
            when(fileStorageService.openObject("k/f.txt")).thenReturn(obj);

            assertNotNull(service.openAssetContent(USER_ID, CONV_ID, 50L));
            assertEquals("f.txt", service.getAssetFileName(USER_ID, CONV_ID, 50L));

            GroupAsset noFile = GroupAsset.builder()
                    .id(51L).conversationId(CONV_ID).uploaderId(USER_ID)
                    .type(GroupAsset.TYPE_ESSENCE).fileKey("").build();
            when(groupAssetMapper.selectOneById(51L)).thenReturn(noFile);
            assertThrows(CustomException.class, () -> service.openAssetContent(USER_ID, CONV_ID, 51L));

            GroupAsset blankName = GroupAsset.builder()
                    .id(52L).conversationId(CONV_ID).uploaderId(USER_ID)
                    .type(GroupAsset.TYPE_FILE).fileKey("k/x").fileName("  ").build();
            when(groupAssetMapper.selectOneById(52L)).thenReturn(blankName);
            assertEquals("file", service.getAssetFileName(USER_ID, CONV_ID, 52L));
        }
    }
}
