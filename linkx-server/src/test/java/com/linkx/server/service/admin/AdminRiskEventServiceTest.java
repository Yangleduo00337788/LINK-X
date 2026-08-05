package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminRiskEventBatchDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventHandleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.vo.AdminReviewBatchResultVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.IpGeoService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.impl.AdminRiskEventServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRiskEventService 风险事件")
class AdminRiskEventServiceTest {

    @Mock SysRiskEventMapper riskEventMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock AdminEventPublisher adminEventPublisher;
    @Mock AdminAudienceService adminAudienceService;
    @Mock AdminUserService adminUserService;
    @Mock RbacService rbacService;
    @Mock IpGeoService ipGeoService;

    private AdminRiskEventServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(adminAudienceService.riskOperatorUserIds()).thenReturn(List.of(1L));
        service = new AdminRiskEventServiceImpl(
                riskEventMapper, sysUserMapper, adminEventPublisher, adminAudienceService, adminUserService,
                rbacService, ipGeoService);
    }

    private SysRiskEvent pendingEvent(Long id) {
        return SysRiskEvent.builder()
                .id(id)
                .eventType(SysRiskEvent.TYPE_SENSITIVE_WORD_MATCH)
                .title("敏感词命中")
                .detail("bad word")
                .riskLevel(SysRiskEvent.LEVEL_MEDIUM)
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(200L)
                .username("bob")
                .ip("192.168.1.1")
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    @Test
    @DisplayName("列表/导出/详情")
    void list_export_detail() {
        when(riskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(riskEventMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(pendingEvent(1L)));
        when(ipGeoService.resolve(anyString())).thenReturn("CN");

        AdminRiskEventQueryDTO q = new AdminRiskEventQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("bob");
        q.setEventStatus(SysRiskEvent.STATUS_PENDING);
        q.setEventType(SysRiskEvent.TYPE_SENSITIVE_WORD_MATCH);
        q.setRiskLevel(SysRiskEvent.LEVEL_MEDIUM);
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());
        assertEquals(1, service.list(q).getTotal());

        assertEquals(1, service.listForExport(q).size());
        when(riskEventMapper.selectOneById(1L)).thenReturn(pendingEvent(1L));
        assertEquals("bob", service.detail(1L).getUsername());

        when(riskEventMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(99L));
    }

    @Test
    @DisplayName("处置 handled/ignored 与权限")
    void handle_actions() {
        SysRiskEvent event = pendingEvent(2L);
        when(riskEventMapper.selectOneById(2L)).thenReturn(event);

        AdminRiskEventHandleDTO handled = new AdminRiskEventHandleDTO();
        handled.setAction("handled");
        handled.setResolution("已核实");
        handled.setUserAction("none");
        service.handle(2L, handled, 9L);
        assertEquals(SysRiskEvent.STATUS_HANDLED, event.getStatus());
        assertEquals("已核实", event.getResolution());
        verify(adminEventPublisher).publishToUsers(eq("risk_handled"), eq(2L), eq(List.of(1L)));

        SysRiskEvent ignored = pendingEvent(3L);
        when(riskEventMapper.selectOneById(3L)).thenReturn(ignored);
        AdminRiskEventHandleDTO ignoreDto = new AdminRiskEventHandleDTO();
        ignoreDto.setAction("ignored");
        service.handle(3L, ignoreDto, 9L);
        assertEquals(SysRiskEvent.STATUS_IGNORED, ignored.getStatus());

        SysRiskEvent withUser = pendingEvent(4L);
        when(riskEventMapper.selectOneById(4L)).thenReturn(withUser);
        when(rbacService.hasPermission(9L, "admin:user:freeze")).thenReturn(true);
        AdminRiskEventHandleDTO freeze = new AdminRiskEventHandleDTO();
        freeze.setAction("handled");
        freeze.setUserAction("freeze");
        service.handle(4L, freeze, 9L);
        verify(adminUserService).freeze(eq(200L), any(AdminUserActionDTO.class), eq(9L));
        assertTrue(withUser.getResolution().contains("[同时冻结用户]"));

        SysRiskEvent banEvent = pendingEvent(5L);
        when(riskEventMapper.selectOneById(5L)).thenReturn(banEvent);
        when(rbacService.hasPermission(9L, "admin:user:ban")).thenReturn(true);
        AdminRiskEventHandleDTO ban = new AdminRiskEventHandleDTO();
        ban.setAction("handled");
        ban.setUserAction("ban");
        service.handle(5L, ban, 9L);
        verify(adminUserService).ban(eq(200L), any(AdminUserActionDTO.class), eq(9L));
    }

    @Test
    @DisplayName("处置校验与 guard")
    void handle_guards() {
        SysRiskEvent done = pendingEvent(10L);
        done.setStatus(SysRiskEvent.STATUS_HANDLED);
        when(riskEventMapper.selectOneById(10L)).thenReturn(done);
        AdminRiskEventHandleDTO dto = new AdminRiskEventHandleDTO();
        dto.setAction("handled");
        assertThrows(CustomException.class, () -> service.handle(10L, dto, 1L));

        SysRiskEvent pending = pendingEvent(11L);
        when(riskEventMapper.selectOneById(11L)).thenReturn(pending);
        AdminRiskEventHandleDTO badAction = new AdminRiskEventHandleDTO();
        badAction.setAction("delete");
        assertThrows(CustomException.class, () -> service.handle(11L, badAction, 1L));

        AdminRiskEventHandleDTO badUserAction = new AdminRiskEventHandleDTO();
        badUserAction.setAction("ignored");
        badUserAction.setUserAction("freeze");
        assertThrows(CustomException.class, () -> service.handle(11L, badUserAction, 1L));

        AdminRiskEventHandleDTO invalidUser = new AdminRiskEventHandleDTO();
        invalidUser.setAction("handled");
        invalidUser.setUserAction("kick");
        assertThrows(CustomException.class, () -> service.handle(11L, invalidUser, 1L));

        SysRiskEvent noUser = pendingEvent(12L);
        noUser.setUserId(null);
        when(riskEventMapper.selectOneById(12L)).thenReturn(noUser);
        AdminRiskEventHandleDTO freezeNoUser = new AdminRiskEventHandleDTO();
        freezeNoUser.setAction("handled");
        freezeNoUser.setUserAction("freeze");
        assertThrows(CustomException.class, () -> service.handle(12L, freezeNoUser, 1L));

        when(rbacService.hasPermission(1L, "admin:user:freeze")).thenReturn(false);
        when(riskEventMapper.selectOneById(13L)).thenReturn(pendingEvent(13L));
        AdminRiskEventHandleDTO noPerm = new AdminRiskEventHandleDTO();
        noPerm.setAction("handled");
        noPerm.setUserAction("freeze");
        assertThrows(CustomException.class, () -> service.handle(13L, noPerm, 1L));

        assertThrows(CustomException.class, () -> service.handle(null, dto, 1L));
    }

    @Test
    @DisplayName("批量处置")
    void batch() {
        SysRiskEvent ok = pendingEvent(20L);
        SysRiskEvent fail = pendingEvent(21L);
        fail.setStatus(SysRiskEvent.STATUS_HANDLED);
        when(riskEventMapper.selectOneById(20L)).thenReturn(ok);
        when(riskEventMapper.selectOneById(21L)).thenReturn(fail);

        AdminRiskEventBatchDTO batch = new AdminRiskEventBatchDTO();
        batch.setAction("handled");
        batch.setResolution("batch");
        List<Long> ids = new ArrayList<>();
        ids.add(20L);
        ids.add(null);
        ids.add(21L);
        batch.setIds(ids);

        AdminReviewBatchResultVO result = service.batch(batch, 5L);
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertEquals(1, result.getFailures().size());

        AdminRiskEventBatchDTO bad = new AdminRiskEventBatchDTO();
        bad.setAction("unknown");
        bad.setIds(List.of(1L));
        assertThrows(CustomException.class, () -> service.batch(bad, 1L));
    }

    @Test
    @DisplayName("计数 API")
    void counts() {
        when(riskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(7L);
        assertEquals(7L, service.countPending());

        Date since = new Date(System.currentTimeMillis() - 86_400_000L);
        assertEquals(7L, service.countSince(since));
        assertEquals(7L, service.countSince(null));
        assertEquals(7L, service.countSinceByType(SysRiskEvent.TYPE_LOGIN_LOCK, since));
        assertEquals(7L, service.countSinceByType(" ", since));
    }

    @Test
    @DisplayName("记录风险事件")
    void record_events() {
        when(sysUserMapper.selectOneById(300L)).thenReturn(SysUser.builder().id(300L).username("carol").build());

        service.recordSensitiveMatch(300L, "bad", "blocked", 88L);
        service.recordSensitiveMatch(300L, "", "alert", null);
        service.recordSensitiveMatch(300L, "x", "other", 1L);
        service.recordMessageStorm(300L, "spam", 50, 9L);
        service.recordLoginLock(300L, "carol", "10.0.0.1", "ADMIN", 15);
        service.recordLoginLock(null, null, "10.0.0.2", null, 10);
        service.recordRateLimit(300L, "user:300", "login", "10.0.0.3");
        service.recordRateLimit(null, null, null, null);

        ArgumentCaptor<SysRiskEvent> captor = ArgumentCaptor.forClass(SysRiskEvent.class);
        verify(riskEventMapper, atLeast(7)).insert(captor.capture());
        verify(adminEventPublisher, atLeast(7)).publishToUsers(eq("risk_created"), any(), eq(List.of(1L)));

        List<SysRiskEvent> events = captor.getAllValues();
        assertTrue(events.stream().anyMatch(e -> SysRiskEvent.LEVEL_HIGH.equals(e.getRiskLevel())
                && SysRiskEvent.TYPE_SENSITIVE_WORD_MATCH.equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> SysRiskEvent.TYPE_MESSAGE_STORM.equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> SysRiskEvent.TYPE_LOGIN_LOCK.equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> SysRiskEvent.TYPE_RATE_LIMIT.equals(e.getEventType())));
    }

    @Test
    @DisplayName("insert 失败不抛出")
    void record_insertFailure() {
        when(sysUserMapper.selectOneById(1L)).thenReturn(null);
        doThrow(new RuntimeException("db down")).when(riskEventMapper).insert(any(SysRiskEvent.class));
        assertDoesNotThrow(() -> service.recordRateLimit(1L, "id", "scope", "1.1.1.1"));
    }

    @Test
    @DisplayName("长 resolution 截断")
    void handle_longResolution() {
        SysRiskEvent event = pendingEvent(30L);
        when(riskEventMapper.selectOneById(30L)).thenReturn(event);
        when(rbacService.hasPermission(1L, "admin:user:ban")).thenReturn(true);

        AdminRiskEventHandleDTO dto = new AdminRiskEventHandleDTO();
        dto.setAction("handled");
        dto.setUserAction("ban");
        dto.setResolution("x".repeat(1000));
        service.handle(30L, dto, 1L);
        assertNotNull(event.getResolution());
        assertTrue(event.getResolution().length() <= 1000);
    }
}
