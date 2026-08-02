package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CreateGroupAnnouncementDTO;
import com.linkx.server.controller.dto.UpdateGroupAnnouncementDTO;
import com.linkx.server.controller.vo.GroupAnnouncementVO;
import com.linkx.server.entity.GroupAnnouncement;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.GroupAnnouncementMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroupAnnouncementServiceImpl 群公告")
class GroupAnnouncementServiceImplTest {

    private static final long OWNER = 1L;
    private static final long GROUP = 100L;

    @Mock GroupAnnouncementMapper announcementMapper;
    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock ObjectProvider<AdminReviewService> adminReviewService;
    @Mock ImMessagePushService imPushService;

    private GroupAnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        when(adminReviewService.getIfAvailable()).thenReturn(null);
        when(sensitiveWordService.filter(anyString())).thenAnswer(inv ->
                new SensitiveWordService.FilterResult(inv.getArgument(0), false, false, false, List.of()));
        service = new GroupAnnouncementServiceImpl(
                announcementMapper, conversationMapper, memberMapper, sysUserMapper,
                sensitiveWordService, adminReviewService, imPushService
        );
    }

    private void stubMember(String role) {
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                ImConversationMember.builder().conversationId(GROUP).userId(OWNER).role(role).build()
        );
        when(conversationMapper.selectOneById(GROUP)).thenReturn(
                ImConversation.builder().id(GROUP).type(ImConversation.TYPE_GROUP).ownerId(OWNER)
                        .name("G").announcement("old").build()
        );
    }

    @Nested
    @DisplayName("读写")
    class Crud {
        @Test
        @DisplayName("list / display")
        void listAndDisplay() {
            stubMember(ImConversationMember.ROLE_OWNER);
            when(announcementMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(announcementMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    GroupAnnouncement.builder().id(1L).conversationId(GROUP).content("hi")
                            .publisherId(OWNER).pinned(1).createTime(new Date()).build()
            ));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUser.builder().id(OWNER).nickname("Owner").build()
            ));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    ImConversationMember.builder().userId(OWNER).role(ImConversationMember.ROLE_OWNER).build()
            ));

            assertEquals(1, service.list(OWNER, GROUP).size());
            GroupAnnouncementVO display = service.display(OWNER, GROUP);
            assertNotNull(display);
            assertEquals("hi", display.getContent());
        }

        @Test
        @DisplayName("create / update / delete / adminDelete")
        void writePaths() {
            stubMember(ImConversationMember.ROLE_OWNER);
            when(announcementMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(announcementMapper.insert(any(GroupAnnouncement.class))).thenAnswer(inv -> {
                GroupAnnouncement a = inv.getArgument(0);
                a.setId(9L);
                a.setCreateTime(new Date());
                return 1;
            });
            when(sysUserMapper.selectOneById(OWNER)).thenReturn(
                    SysUser.builder().id(OWNER).nickname("Owner").build());
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    ImConversationMember.builder().userId(OWNER).role(ImConversationMember.ROLE_OWNER).build()
            ));
            when(announcementMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            CreateGroupAnnouncementDTO create = new CreateGroupAnnouncementDTO();
            create.setContent("  hello  ");
            create.setPinned(true);
            GroupAnnouncementVO created = service.create(OWNER, GROUP, create);
            assertEquals(9L, created.getId());

            GroupAnnouncement row = GroupAnnouncement.builder()
                    .id(9L).conversationId(GROUP).content("hello").publisherId(OWNER).pinned(1).build();
            when(announcementMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(row);
            when(announcementMapper.selectOneById(9L)).thenReturn(row);

            UpdateGroupAnnouncementDTO upd = new UpdateGroupAnnouncementDTO();
            upd.setContent("updated");
            upd.setPinned(false);
            assertEquals("updated", service.update(OWNER, GROUP, 9L, upd).getContent());

            service.delete(OWNER, GROUP, 9L);
            verify(announcementMapper, atLeastOnce()).deleteById(9L);

            service.adminDelete(9L);
            verify(announcementMapper, atLeast(2)).deleteById(9L);
        }

        @Test
        @DisplayName("非成员 403")
        void notMember() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class, () -> service.list(OWNER, GROUP));
        }

        @Test
        @DisplayName("adminDelete 不存在")
        void adminDeleteMissing() {
            when(announcementMapper.selectOneById(1L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.adminDelete(1L));
        }
    }
}
