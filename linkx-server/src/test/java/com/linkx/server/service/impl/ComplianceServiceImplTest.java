package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.entity.CloudFile;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.Note;
import com.linkx.server.entity.RedPacket;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.entity.UserPreference;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.BalanceLogMapper;
import com.linkx.server.mapper.CalendarEventMapper;
import com.linkx.server.mapper.CloudActivityMapper;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.CloudFileTagMapper;
import com.linkx.server.mapper.CloudFolderMapper;
import com.linkx.server.mapper.CloudShareMapper;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.FavoriteStorageMapper;
import com.linkx.server.mapper.FavoriteTagMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.MomentsCommentMapper;
import com.linkx.server.mapper.MomentsImageMapper;
import com.linkx.server.mapper.MomentsLikeMapper;
import com.linkx.server.mapper.MomentsPostMapper;
import com.linkx.server.mapper.NoteMapper;
import com.linkx.server.mapper.RedPacketMapper;
import com.linkx.server.mapper.RedPacketRecordMapper;
import com.linkx.server.mapper.SysFriendRequestMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.mapper.UserBalanceMapper;
import com.linkx.server.mapper.UserBlacklistMapper;
import com.linkx.server.mapper.UserPreferenceMapper;
import com.linkx.server.mapper.UserStorageMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.core.query.QueryConditionBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ComplianceServiceImpl 合规")
class ComplianceServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final String PASSWORD = "Test1234abcd";

    @Mock SysUserMapper userMapper;
    @Mock SysUserRelationMapper relationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock ImMessageMapper messageMapper;
    @Mock DeviceSessionMapper deviceSessionMapper;
    @Mock NoteMapper noteMapper;
    @Mock CloudFileMapper cloudFileMapper;
    @Mock CloudFolderMapper cloudFolderMapper;
    @Mock CloudFileTagMapper cloudFileTagMapper;
    @Mock CloudShareMapper cloudShareMapper;
    @Mock CloudActivityMapper cloudActivityMapper;
    @Mock FavoriteMapper favoriteMapper;
    @Mock FavoriteTagMapper favoriteTagMapper;
    @Mock FavoriteStorageMapper favoriteStorageMapper;
    @Mock MomentsPostMapper momentsPostMapper;
    @Mock MomentsImageMapper momentsImageMapper;
    @Mock MomentsLikeMapper momentsLikeMapper;
    @Mock MomentsCommentMapper momentsCommentMapper;
    @Mock CalendarEventMapper calendarEventMapper;
    @Mock UserBalanceMapper userBalanceMapper;
    @Mock BalanceLogMapper balanceLogMapper;
    @Mock RedPacketMapper redPacketMapper;
    @Mock RedPacketRecordMapper redPacketRecordMapper;
    @Mock FeedbackMapper feedbackMapper;
    @Mock MessageNotificationMapper messageNotificationMapper;
    @Mock UserPreferenceMapper userPreferenceMapper;
    @Mock UserStorageMapper userStorageMapper;
    @Mock GroupInvitationMapper groupInvitationMapper;
    @Mock SysFriendRequestMapper friendRequestMapper;
    @Mock UserBlacklistMapper userBlacklistMapper;
    @Mock DeviceSessionService deviceSessionService;
    @Mock TokenService tokenService;
    @Mock AuditLogService auditLogService;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;

    private ComplianceServiceImpl service;

    @BeforeEach
    void setUp() {
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
        service = new ComplianceServiceImpl(
                userMapper, relationMapper, memberMapper, messageMapper, deviceSessionMapper,
                noteMapper, cloudFileMapper, cloudFolderMapper, cloudFileTagMapper, cloudShareMapper,
                cloudActivityMapper, favoriteMapper, favoriteTagMapper, favoriteStorageMapper,
                momentsPostMapper, momentsImageMapper, momentsLikeMapper, momentsCommentMapper,
                calendarEventMapper, userBalanceMapper, balanceLogMapper, redPacketMapper,
                redPacketRecordMapper, feedbackMapper, messageNotificationMapper, userPreferenceMapper,
                userStorageMapper, groupInvitationMapper, friendRequestMapper, userBlacklistMapper,
                deviceSessionService, tokenService, auditLogService, fileStorageService, mediaUrlService
        );
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private SysUser activeUser() {
        return SysUser.builder()
                .id(USER_ID)
                .username("alice")
                .nickname("Alice")
                .email("alice@test.com")
                .phone("13800138000")
                .avatar("avatars/alice.png")
                .password(PasswordEncoderHolder.encode(PASSWORD))
                .status(1)
                .build();
    }

    private void stubEmptyPurgeQueries() {
        when(userPreferenceMapper.selectOneById(USER_ID)).thenReturn(null);
        when(cloudFileMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(momentsPostMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(redPacketMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private UpdateChain stubUpdateChain() {
        UpdateChain chain = mock(UpdateChain.class);
        QueryConditionBuilder condition = mock(QueryConditionBuilder.class);
        lenient().when(chain.set(any(com.mybatisflex.core.util.LambdaGetter.class), any())).thenReturn(chain);
        lenient().when(chain.where(any(com.mybatisflex.core.util.LambdaGetter.class))).thenReturn(condition);
        lenient().doReturn(chain).when(condition).eq(any());
        lenient().doReturn(chain).when(condition).eq(anyLong());
        lenient().when(chain.update()).thenReturn(true);
        return chain;
    }

    private void withMessageUpdateChain(Runnable action) {
        UpdateChain chain = stubUpdateChain();
        try (MockedStatic<UpdateChain> updateChain = mockStatic(UpdateChain.class)) {
            updateChain.when(() -> UpdateChain.of(ImMessage.class)).thenReturn(chain);
            action.run();
        }
    }

    @Nested
    @DisplayName("exportUserData")
    class Export {
        @Test
        @DisplayName("用户不存在")
        void userMissing() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.exportUserData(USER_ID));
        }

        @Test
        @DisplayName("导出成功")
        void success() {
            SysUser user = activeUser();
            when(userMapper.selectOneById(USER_ID)).thenReturn(user);

            SysUserRelation friend = SysUserRelation.builder()
                    .userId(USER_ID).friendId(99L).remark("Bob").createTime(new Date()).build();
            when(relationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(friend));

            ImConversationMember convMember = ImConversationMember.builder()
                    .conversationId(100L).userId(USER_ID).role("member").createTime(new Date()).build();
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(convMember));

            ImMessage msg = ImMessage.builder()
                    .id(1L).conversationId(100L).senderId(USER_ID).type("text")
                    .content("hello").createTime(new Date()).build();
            when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(msg));

            DeviceSession device = DeviceSession.builder()
                    .deviceId("d1").deviceName("Phone").deviceType("mobile")
                    .ip("127.0.0.1").lastActive(new Date()).build();
            when(deviceSessionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(device));

            Note note = Note.builder().id(5L).title("memo").createTime(new Date()).build();
            when(noteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(note));

            UserDataExportVO vo = service.exportUserData(USER_ID);
            assertEquals(USER_ID, vo.getUserId());
            assertEquals("alice", vo.getUsername());
            assertEquals(1, vo.getFriends().size());
            assertEquals(1, vo.getConversations().size());
            assertEquals(1, vo.getRecentMessages().size());
            assertEquals(1, vo.getDevices().size());
            assertEquals(1, vo.getNotes().size());
            verify(auditLogService).log(
                    eq(SysAuditLog.OperationType.DATA_EXPORT),
                    eq("用户导出个人数据"),
                    eq(USER_ID), isNull(), isNull(), isNull(), eq(true), isNull());
        }

        @Test
        @DisplayName("无会话时不查消息")
        void noConversationsSkipsMessages() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(activeUser());
            when(relationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(deviceSessionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(noteMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

            UserDataExportVO vo = service.exportUserData(USER_ID);
            assertTrue(vo.getRecentMessages().isEmpty());
            verify(messageMapper, never()).selectListByQuery(any(QueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("purgeUserData")
    class Purge {
        @Test
        @DisplayName("用户不存在")
        void userMissing() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.purgeUserData(USER_ID, PASSWORD));
        }

        @Test
        @DisplayName("密码错误")
        void wrongPassword() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(activeUser());
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.purgeUserData(USER_ID, "bad"));
            assertEquals(400, ex.getCode());
            verify(auditLogService).log(
                    eq(SysAuditLog.OperationType.DATA_PURGE),
                    eq("合规清除密码校验失败"),
                    eq(USER_ID), isNull(), isNull(), isNull(), eq(false),
                    eq("合规清除密码校验失败"));
        }

        @Test
        @DisplayName("空密码")
        void blankPassword() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(activeUser());
            assertThrows(CustomException.class, () -> service.purgeUserData(USER_ID, ""));
        }

        @Test
        @DisplayName("成功清除并在事务提交后删 MinIO")
        void successWithTxSync() {
            SysUser user = activeUser();
            when(userMapper.selectOneById(USER_ID)).thenReturn(user);
            stubEmptyPurgeQueries();
            when(userPreferenceMapper.selectOneById(USER_ID)).thenReturn(
                    UserPreference.builder().userId(USER_ID).momentsBackground("bg/user.png").build()
            );
            when(cloudFileMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    CloudFile.builder().fileKey("cloud/file.pdf").build()
            ));

            TransactionSynchronizationManager.initSynchronization();
            withMessageUpdateChain(() -> service.purgeUserData(USER_ID, PASSWORD));

            assertEquals("已注销用户", user.getNickname());
            assertEquals(0, user.getStatus());
            assertNull(user.getEmail());
            verify(userMapper).update(user);
            verify(deviceSessionService).deleteAllByUser(USER_ID);
            verify(tokenService).revokeAllUserTokens(USER_ID);
            verify(fileStorageService, never()).deleteFile(anyString());

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(fileStorageService).deleteFile("avatars/alice.png");
            verify(fileStorageService).deleteFile("bg/user.png");
            verify(fileStorageService).deleteFile("cloud/file.pdf");
            verify(auditLogService).log(
                    eq(SysAuditLog.OperationType.DATA_PURGE),
                    eq("用户数据清除完成"),
                    eq(USER_ID), isNull(), isNull(), isNull(), eq(true), isNull());
        }

        @Test
        @DisplayName("无事务时立即删 MinIO")
        void successWithoutTx() {
            SysUser user = activeUser();
            user.setAvatar("/default-avatar.svg");
            when(userMapper.selectOneById(USER_ID)).thenReturn(user);
            stubEmptyPurgeQueries();

            withMessageUpdateChain(() -> service.purgeUserData(USER_ID, PASSWORD));

            verify(fileStorageService, never()).deleteFile(anyString());
            verify(auditLogService).log(
                    eq(SysAuditLog.OperationType.DATA_PURGE),
                    eq("用户数据清除完成"),
                    eq(USER_ID), isNull(), isNull(), isNull(), eq(true), isNull());
        }

        @Test
        @DisplayName("清除含活跃红包的发送记录")
        void purgesActiveRedPackets() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(activeUser());
            stubEmptyPurgeQueries();
            RedPacket packet = RedPacket.builder()
                    .id(7L).senderId(USER_ID).status(RedPacket.STATUS_ACTIVE)
                    .remainingAmount(new BigDecimal("1.00")).remainingCount(1).build();
            when(redPacketMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(packet));

            withMessageUpdateChain(() -> service.purgeUserData(USER_ID, PASSWORD));

            verify(redPacketRecordMapper, atLeastOnce()).deleteByQuery(any(QueryWrapper.class));
            verify(redPacketMapper).deleteByQuery(any(QueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("audit")
    class Audit {
        @Test
        @DisplayName("retention / purge / export 映射")
        void actionTypes() {
            service.audit(USER_ID, "retention", "保留策略", true);
            verify(auditLogService).log(
                    eq(SysAuditLog.OperationType.DATA_RETENTION),
                    eq("保留策略"), eq(USER_ID), isNull(), isNull(), isNull(), eq(true), isNull());

            service.audit(USER_ID, "purge", "清除", false);
            verify(auditLogService).log(
                    eq(SysAuditLog.OperationType.DATA_PURGE),
                    eq("清除"), eq(USER_ID), isNull(), isNull(), isNull(), eq(false), eq("清除"));
        }
    }
}
