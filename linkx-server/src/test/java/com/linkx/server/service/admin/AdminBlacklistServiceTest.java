package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminBlacklistAddDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistQueryDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistReleaseDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.vo.AdminBlacklistVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysAdminBlacklist;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysAdminBlacklistMapper;
import com.linkx.server.service.admin.impl.AdminBlacklistServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBlacklistService 平台黑名单")
class AdminBlacklistServiceTest {

    @Mock
    private SysAdminBlacklistMapper blacklistMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private AdminUserService adminUserService;

    private AdminBlacklistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminBlacklistServiceImpl(blacklistMapper, sysUserMapper, adminUserService);
    }

    @Test
    @DisplayName("加入黑名单应委托 ban")
    void add_delegatesToBan() {
        AdminBlacklistAddDTO dto = new AdminBlacklistAddDTO();
        dto.setUserId(100L);
        dto.setReason("spam");

        service.add(dto, 1L);

        ArgumentCaptor<AdminUserActionDTO> captor = ArgumentCaptor.forClass(AdminUserActionDTO.class);
        verify(adminUserService).ban(eq(100L), captor.capture(), eq(1L));
        assertEquals("spam", captor.getValue().getReason());
    }

    @Test
    @DisplayName("recordBan 无生效记录时应插入")
    void recordBan_insertsWhenNoActive() {
        when(sysUserMapper.selectOneById(100L)).thenReturn(SysUser.builder()
                .id(100L).username("alice").nickname("Alice").build());
        when(blacklistMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        service.recordBan(100L, "abuse", 9L);

        ArgumentCaptor<SysAdminBlacklist> captor = ArgumentCaptor.forClass(SysAdminBlacklist.class);
        verify(blacklistMapper).insert(captor.capture());
        SysAdminBlacklist row = captor.getValue();
        assertEquals(100L, row.getUserId());
        assertEquals("alice", row.getUsername());
        assertEquals("abuse", row.getReason());
        assertEquals(SysAdminBlacklist.STATUS_ACTIVE, row.getStatus());
        assertEquals(9L, row.getCreatedBy());
    }

    @Test
    @DisplayName("recordBan 已有生效记录时应更新原因")
    void recordBan_updatesExistingActive() {
        SysAdminBlacklist active = SysAdminBlacklist.builder()
                .id(55L)
                .userId(100L)
                .username("alice")
                .status(SysAdminBlacklist.STATUS_ACTIVE)
                .reason("old")
                .build();
        when(sysUserMapper.selectOneById(100L)).thenReturn(SysUser.builder()
                .id(100L).username("alice").nickname("Alice").build());
        when(blacklistMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);

        service.recordBan(100L, "new-reason", 9L);

        verify(blacklistMapper, never()).insert(any());
        verify(blacklistMapper).update(active);
        assertEquals("new-reason", active.getReason());
        assertEquals(9L, active.getCreatedBy());
    }

    @Test
    @DisplayName("releaseByUserId 应标记 released")
    void releaseByUserId_marksReleased() {
        SysAdminBlacklist active = SysAdminBlacklist.builder()
                .id(55L)
                .userId(100L)
                .status(SysAdminBlacklist.STATUS_ACTIVE)
                .build();
        when(blacklistMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(active);

        service.releaseByUserId(100L, "ok", 2L);

        verify(blacklistMapper).update(active);
        assertEquals(SysAdminBlacklist.STATUS_RELEASED, active.getStatus());
        assertEquals(2L, active.getReleasedBy());
        assertEquals("ok", active.getReleaseReason());
        assertNotNull(active.getReleasedAt());
    }

    @Test
    @DisplayName("release 非生效记录应拒绝")
    void release_rejectsAlreadyReleased() {
        when(blacklistMapper.selectOneById(55L)).thenReturn(SysAdminBlacklist.builder()
                .id(55L)
                .userId(100L)
                .status(SysAdminBlacklist.STATUS_RELEASED)
                .build());

        CustomException ex = assertThrows(CustomException.class,
                () -> service.release(55L, new AdminBlacklistReleaseDTO(), 1L));
        assertEquals(400, ex.getCode());
        verify(adminUserService, never()).unban(any(), any());
    }

    @Test
    @DisplayName("list 默认按生效状态可查询")
    void list_returnsMappedRows() {
        SysAdminBlacklist row = SysAdminBlacklist.builder()
                .id(1L)
                .userId(100L)
                .username("alice")
                .status(SysAdminBlacklist.STATUS_ACTIVE)
                .createdBy(9L)
                .createTime(new Date())
                .build();
        when(blacklistMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(blacklistMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(row));
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUser.builder().id(9L).username("admin").build()));

        AdminBlacklistQueryDTO query = new AdminBlacklistQueryDTO();
        query.setPage(1);
        query.setSize(20);
        query.setEntryStatus("active");

        var page = service.list(query);
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getItems().size());
        AdminBlacklistVO vo = page.getItems().get(0);
        assertEquals("alice", vo.getUsername());
        assertEquals("admin", vo.getCreatedByName());
    }

    @Test
    @DisplayName("detail 不存在应 404")
    void detail_notFound() {
        when(blacklistMapper.selectOneById(1L)).thenReturn(null);
        CustomException ex = assertThrows(CustomException.class, () -> service.detail(1L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("releaseByUserId 无生效记录时不报错")
    void releaseByUserId_noopWhenAbsent() {
        when(blacklistMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertDoesNotThrow(() -> service.releaseByUserId(100L, null, 1L));
        verify(blacklistMapper, never()).update(any());
        verify(blacklistMapper, never()).insert(any());
        verifyNoMoreInteractions(adminUserService);
    }
}
