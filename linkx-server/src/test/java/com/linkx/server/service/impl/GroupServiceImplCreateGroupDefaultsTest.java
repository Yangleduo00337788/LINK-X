package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.GroupAnnouncementMapper;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplCreateGroupDefaultsTest {

    private static final long CREATOR_ID = 1L;
    private static final long FRIEND_ID = 2L;

    @Mock
    private ImConversationMapper conversationMapper;
    @Mock
    private ImConversationMemberMapper memberMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRelationMapper relationMapper;
    @Mock
    private MediaUrlService mediaUrlService;
    @Mock
    private ChatService chatService;
    @Mock
    private ImMessagePushService imPushService;
    @Mock
    private MessageNotificationService notificationService;
    @Mock
    private MessageNotificationMapper notificationMapper;
    @Mock
    private GroupAnnouncementMapper groupAnnouncementMapper;
    @Mock
    private GroupAssetMapper groupAssetMapper;
    @Mock
    private GroupInvitationMapper groupInvitationMapper;
    @Mock
    private ImMessageRepository imMessageRepository;
    @Mock
    private FileStorageService fileStorageService;

    private final LinkxProperties linkxProperties = new LinkxProperties();
    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(
                conversationMapper,
                memberMapper,
                sysUserMapper,
                relationMapper,
                mediaUrlService,
                chatService,
                imPushService,
                notificationService,
                notificationMapper,
                groupAnnouncementMapper,
                groupAssetMapper,
                groupInvitationMapper,
                imMessageRepository,
                fileStorageService,
                linkxProperties);
    }

    @Test
    void createGroup_appliesGroupAiDefaultsFromRuntimeSettings() {
        configureGroupAiDefaults(false, true, true, "产品发布", "提炼待办");
        stubSuccessfulCreateFlow();

        CreateGroupDTO dto = new CreateGroupDTO();
        dto.setName("测试群");
        dto.setMemberIds(List.of(FRIEND_ID));

        GroupConversationVO vo = groupService.createGroup(CREATOR_ID, dto);

        ImConversation created = captureInsertedGroup();
        assertEquals(0, created.getLinkmateEnabled());
        assertEquals(1, created.getGroupAiProactiveEnabled());
        assertEquals(1, created.getGroupAiSmartSummaryEnabled());
        assertEquals("产品发布", created.getGroupAiInterestTopics());
        assertEquals("提炼待办", created.getGroupAiSummaryInstruction());
        assertFalse(vo.getLinkmateEnabled());
        assertTrue(vo.getGroupAiProactiveEnabled());
        assertTrue(vo.getGroupAiSmartSummaryEnabled());
    }

    @Test
    void createGroup_appliesAllOffDefaults() {
        configureGroupAiDefaults(false, false, false, "", "");
        stubSuccessfulCreateFlow();

        CreateGroupDTO dto = new CreateGroupDTO();
        dto.setName("默认关群");
        dto.setMemberIds(List.of(FRIEND_ID));

        groupService.createGroup(CREATOR_ID, dto);

        ImConversation created = captureInsertedGroup();
        assertEquals(0, created.getLinkmateEnabled());
        assertEquals(0, created.getGroupAiProactiveEnabled());
        assertEquals(0, created.getGroupAiSmartSummaryEnabled());
        assertNull(created.getGroupAiInterestTopics());
        assertNull(created.getGroupAiSummaryInstruction());
    }

    private void configureGroupAiDefaults(
            boolean linkmateDefault,
            boolean proactiveDefault,
            boolean summaryDefault,
            String topics,
            String instruction) {
        LinkxProperties.GroupAi groupAi = linkxProperties.getGroupAi();
        groupAi.setLinkmateDefaultEnabled(linkmateDefault);
        groupAi.setProactiveDefaultEnabled(proactiveDefault);
        groupAi.setSmartSummaryDefaultEnabled(summaryDefault);
        groupAi.setDefaultInterestTopics(topics);
        groupAi.setDefaultSummaryInstruction(instruction);
    }

    private void stubSuccessfulCreateFlow() {
        SysUser creator = SysUser.builder().id(CREATOR_ID).nickname("群主").username("owner").build();
        SysUser friend = SysUser.builder().id(FRIEND_ID).nickname("好友").username("friend").build();
        when(sysUserMapper.selectOneById(CREATOR_ID)).thenReturn(creator);
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(friend), List.of());
        when(relationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        doAnswer(invocation -> {
            ImConversation group = invocation.getArgument(0);
            group.setId(100L);
            return 1;
        }).when(conversationMapper).insert(any(ImConversation.class));
        when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                ImConversationMember.builder().conversationId(100L).userId(CREATOR_ID).build());
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(mediaUrlService.resolve(any())).thenReturn(null);
    }

    private ImConversation captureInsertedGroup() {
        ArgumentCaptor<ImConversation> captor = ArgumentCaptor.forClass(ImConversation.class);
        org.mockito.Mockito.verify(conversationMapper).insert(captor.capture());
        return captor.getValue();
    }
}
