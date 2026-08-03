package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminAbnormalAccessQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRateLimitHitVO;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.IpGeoService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.admin.impl.AdminAbnormalAccessServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAbnormalAccessService 异常访问")
class AdminAbnormalAccessServiceTest {

    @Mock SysLoginAuditMapper sysLoginAuditMapper;
    @Mock SysRiskEventMapper riskEventMapper;
    @Mock RateLimitService rateLimitService;
    @Mock IpGeoService ipGeoService;

    private AdminAbnormalAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAbnormalAccessServiceImpl(
                sysLoginAuditMapper, riskEventMapper, rateLimitService, ipGeoService);
    }

    @Test
    @DisplayName("登录失败列表")
    void list_loginFail() {
        when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(sysLoginAuditMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(SysLoginAudit.builder()
                        .id(1L)
                        .username("alice")
                        .ip("1.2.3.4")
                        .reason("bad password")
                        .success(0)
                        .createTime(new Date())
                        .build()));
        when(ipGeoService.resolve(anyString())).thenReturn("CN");

        AdminAbnormalAccessQueryDTO q = new AdminAbnormalAccessQueryDTO();
        q.setSource("login_fail");
        var page = service.list(q);
        assertEquals(1, page.getTotal());
        assertEquals("login_fail", page.getItems().get(0).getSource());
        assertEquals("alice", page.getItems().get(0).getUsername());
    }

    @Test
    @DisplayName("限流命中列表")
    void list_rateLimit() {
        when(rateLimitService.listActiveHits(isNull(), anyInt()))
                .thenReturn(List.of(AdminRateLimitHitVO.builder()
                        .redisKey("k1")
                        .scope("login-fail")
                        .ip("9.9.9.9")
                        .identity("bob")
                        .count(5)
                        .ttlSeconds(60L)
                        .build()));
        when(ipGeoService.resolve(anyString())).thenReturn("US");

        AdminAbnormalAccessQueryDTO q = new AdminAbnormalAccessQueryDTO();
        q.setSource("rate_limit");
        var page = service.list(q);
        assertEquals(1, page.getTotal());
        assertEquals("rate_limit", page.getItems().get(0).getSource());
        assertEquals(5L, page.getItems().get(0).getHitCount());
    }

    @Test
    @DisplayName("概览统计")
    void summary_ok() {
        when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(12L);
        when(rateLimitService.listActiveHits(isNull(), eq(500))).thenReturn(List.of());
        when(riskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);

        var summary = service.summary();
        assertEquals(12L, summary.getLoginFail24h());
        assertEquals(0L, summary.getRateLimitActive());
        assertEquals(3L, summary.getRiskEventPending());
    }

    @Test
    @DisplayName("合并视图")
    void list_merged() {
        when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L, 1L);
        when(sysLoginAuditMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(SysLoginAudit.builder()
                        .id(2L).username("u").success(0).createTime(new Date()).build()));
        when(riskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L, 1L);
        when(riskEventMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(SysRiskEvent.builder()
                        .id(3L)
                        .eventType(SysRiskEvent.TYPE_LOGIN_LOCK)
                        .title("locked")
                        .status(SysRiskEvent.STATUS_PENDING)
                        .createTime(new Date())
                        .build()));
        when(rateLimitService.listActiveHits(isNull(), anyInt())).thenReturn(List.of());
        when(ipGeoService.resolve(any())).thenReturn(null);

        AdminAbnormalAccessQueryDTO q = new AdminAbnormalAccessQueryDTO();
        q.setSource("all");
        var page = service.list(q);
        assertTrue(page.getTotal() >= 2);
        assertFalse(page.getItems().isEmpty());
    }
}
