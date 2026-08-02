package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.CloudFileVO;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudFileServiceImpl 云文件聚合")
class CloudFileServiceImplTest {

    @Mock ImConversationMemberMapper memberMapper;
    @Mock ImConversationMapper conversationMapper;
    @Mock ImMessageMapper messageMapper;
    @Mock GroupAssetMapper groupAssetMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MediaUrlService mediaUrlService;

    private CloudFileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudFileServiceImpl(
                memberMapper, conversationMapper, messageMapper, groupAssetMapper, sysUserMapper, mediaUrlService);
    }

    @Test
    @DisplayName("无会话成员返回空")
    void emptyMembership() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        assertTrue(service.listMine(1L, null, 20).isEmpty());
    }

    @Test
    @DisplayName("聚合聊天文件与群资产并分类")
    void listMine_aggregates() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                List.of(
                        ImConversationMember.builder().conversationId(10L).userId(1L).build(),
                        ImConversationMember.builder().conversationId(10L).userId(2L).build(),
                        ImConversationMember.builder().conversationId(11L).userId(1L).build()
                ),
                List.of(ImConversationMember.builder().conversationId(10L).userId(2L).build())
        );
        when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                ImConversation.builder().id(10L).type(ImConversation.TYPE_PRIVATE).build(),
                ImConversation.builder().id(11L).type(ImConversation.TYPE_GROUP).name("G1").build()
        ));
        when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                ImMessage.builder()
                        .id(100L).conversationId(10L).senderId(2L)
                        .type(ImMessage.TYPE_FILE).fileName("doc.pdf").fileSize(12L)
                        .fileUrl("files/a.pdf").createTime(new Date())
                        .build(),
                ImMessage.builder()
                        .id(101L).conversationId(11L).senderId(1L)
                        .type(ImMessage.TYPE_IMAGE).fileName("a.png").fileSize(8L)
                        .fileUrl("img/a.png").createTime(new Date(System.currentTimeMillis() - 1000))
                        .build()
        ));
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                List.of(SysUser.builder().id(2L).username("bob").nickname("Bob").build()),
                List.of(SysUser.builder().id(3L).username("carol").nickname("Carol").build())
        );
        when(groupAssetMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                GroupAsset.builder()
                        .id(200L).conversationId(11L).uploaderId(3L)
                        .type(GroupAsset.TYPE_FILE).fileName("clip.mp4").fileSize(99L)
                        .fileKey("g/clip.mp4").title("clip").createTime(new Date())
                        .build()
        ));
        when(mediaUrlService.resolveFile(anyString())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));

        List<CloudFileVO> all = service.listMine(1L, null, 50);
        assertTrue(all.size() >= 2);
        assertTrue(all.stream().anyMatch(v -> "document".equals(v.getCategory())));
        assertTrue(all.stream().anyMatch(v -> "media".equals(v.getCategory()) || "image".equals(v.getCategory())));

        List<CloudFileVO> images = service.listMine(1L, "image", 10);
        assertTrue(images.stream().allMatch(v -> "image".equals(v.getCategory())));
    }
}
