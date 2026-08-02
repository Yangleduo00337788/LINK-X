package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminNoticeDTO;
import com.linkx.server.controller.admin.dto.AdminNoticeQueryDTO;
import com.linkx.server.controller.admin.vo.AdminNoticeVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysAdminNotice;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysAdminNoticeMapper;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.impl.AdminNoticeServiceImpl;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNoticeService 公告")
class AdminNoticeServiceTest {

    @Mock SysAdminNoticeMapper noticeMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MessageNotificationService notificationService;
    @Mock ImMessagePushService imPushService;
    @Mock AdminEventPublisher adminEventPublisher;

    private AdminNoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminNoticeServiceImpl(
                noticeMapper, sysUserMapper, notificationService, imPushService, adminEventPublisher);
    }

    private SysAdminNotice draft(Long id, String side) {
        return SysAdminNotice.builder()
                .id(id)
                .title("Hello")
                .content("Body text")
                .targetSide(side)
                .status(SysAdminNotice.STATUS_DRAFT)
                .deleted(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private AdminNoticeDTO dto(String side) {
        AdminNoticeDTO d = new AdminNoticeDTO();
        d.setTitle(" Hello ");
        d.setContent(" Body ");
        d.setTargetSide(side);
        return d;
    }

    @Test
    @DisplayName("列表/详情/创建/更新/删除")
    void crud() {
        when(noticeMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(noticeMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draft(1L, "admin")));

        AdminNoticeQueryDTO q = new AdminNoticeQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("Hello");
        q.setNoticeStatus(SysAdminNotice.STATUS_DRAFT);
        q.setTargetSide("admin");
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());
        assertEquals(1, service.list(q).getTotal());

        when(noticeMapper.selectOneById(1L)).thenReturn(draft(1L, "admin"));
        assertEquals("Hello", service.detail(1L).getTitle());

        when(noticeMapper.insert(any(SysAdminNotice.class))).thenAnswer(inv -> {
            ((SysAdminNotice) inv.getArgument(0)).setId(9L);
            return 1;
        });
        AdminNoticeVO created = service.create(dto("admin"), 3L);
        assertEquals(9L, created.getId());
        verify(adminEventPublisher).publish(eq("notice_created"), eq(9L), anyString());

        SysAdminNotice entity = draft(2L, "admin");
        when(noticeMapper.selectOneById(2L)).thenReturn(entity);
        service.update(2L, dto("client"), 3L);
        assertEquals(SysAdminNotice.TARGET_CLIENT, entity.getTargetSide());
        service.delete(2L, 3L);
        assertEquals(1, entity.getDeleted());
    }

    @Test
    @DisplayName("发布管理端与客户端；下线")
    void publish_paths() {
        SysAdminNotice adminNotice = draft(10L, SysAdminNotice.TARGET_ADMIN);
        when(noticeMapper.selectOneById(10L)).thenReturn(adminNotice);
        service.publish(10L, 1L);
        assertEquals(SysAdminNotice.STATUS_PUBLISHED, adminNotice.getStatus());
        verify(adminEventPublisher).publish(eq("admin_notice_published"), eq(10L), anyString());

        service.unpublish(10L, 1L);
        assertEquals(SysAdminNotice.STATUS_UNPUBLISHED, adminNotice.getStatus());
        verify(adminEventPublisher).publish(eq("admin_notice_unpublished"), eq(10L), anyString());

        SysAdminNotice clientNotice = draft(11L, SysAdminNotice.TARGET_CLIENT);
        when(noticeMapper.selectOneById(11L)).thenReturn(clientNotice);
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUser.builder().id(5L).build()
        ));
        when(notificationService.createForUsers(anyList(), eq(1L), anyString(), isNull(),
                eq(AdminNoticeServiceImpl.NOTICE_TYPE), eq(11L), anyString())).thenReturn(1);

        service.publish(11L, 1L);
        verify(imPushService).pushToAllOnline(eq("notification_refresh"), anyMap());

        when(notificationService.deleteByTypeAndRelatedId(
                AdminNoticeServiceImpl.NOTICE_TYPE, 11L)).thenReturn(1);
        service.unpublish(11L, 1L);
        verify(notificationService).deleteByTypeAndRelatedId(AdminNoticeServiceImpl.NOTICE_TYPE, 11L);
    }

    @Test
    @DisplayName("已发布不可编辑；inbox 过滤")
    void guards_and_inbox() {
        SysAdminNotice published = draft(12L, "admin");
        published.setStatus(SysAdminNotice.STATUS_PUBLISHED);
        when(noticeMapper.selectOneById(12L)).thenReturn(published);
        assertThrows(CustomException.class, () -> service.update(12L, dto("admin"), 1L));
        assertThrows(CustomException.class, () -> service.delete(12L, 1L));

        when(noticeMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(noticeMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        assertEquals(0, service.listInbox(new AdminNoticeQueryDTO()).getTotal());
    }
}
