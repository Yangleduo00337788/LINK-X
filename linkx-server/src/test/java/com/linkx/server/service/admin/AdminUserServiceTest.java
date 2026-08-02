package com.linkx.server.service.admin;

import com.linkx.server.common.DataScopeContext;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserResetPasswordDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserResetPasswordVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.SysDept;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserDeviceBinding;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeptMapper;
import com.linkx.server.mapper.SysDeviceBanMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserDeviceBindingMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.IpGeoService;
import com.linkx.server.service.PasswordPolicyService;
import com.linkx.server.service.PresenceService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.TokenService;
import com.linkx.server.service.admin.impl.AdminUserServiceImpl;
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

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminUserService 用户管理")
class AdminUserServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long OPERATOR_ID = 200L;

    @Mock SysUserMapper sysUserMapper;
    @Mock SysDeptMapper sysDeptMapper;
    @Mock SysLoginAuditMapper sysLoginAuditMapper;
    @Mock SysDeviceBanMapper deviceBanMapper;
    @Mock SysUserDeviceBindingMapper deviceBindingMapper;
    @Mock RbacService rbacService;
    @Mock DeviceSessionService deviceSessionService;
    @Mock PresenceService presenceService;
    @Mock TokenService tokenService;
    @Mock AdminBlacklistService adminBlacklistService;
    @Mock PasswordPolicyService passwordPolicyService;
    @Mock AuditLogService auditLogService;
    @Mock IpGeoService ipGeoService;

    private LinkxProperties linkxProperties;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getAuth().setPasswordMinLength(8);
        linkxProperties.getAuth().setPasswordMaxLength(32);
        DataScopeContext.setUnrestricted();
        service = new AdminUserServiceImpl(
                sysUserMapper, sysDeptMapper, sysLoginAuditMapper, deviceBanMapper, deviceBindingMapper,
                rbacService, deviceSessionService, presenceService, tokenService, adminBlacklistService,
                passwordPolicyService, auditLogService, ipGeoService, linkxProperties);
        stubRegularUser(USER_ID);
        stubOperator(OPERATOR_ID);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private SysUser user(Long id, int status) {
        return SysUser.builder()
                .id(id)
                .username("user" + id)
                .nickname("Nick" + id)
                .email("u" + id + "@test.com")
                .phone("1380000" + id)
                .status(status)
                .deptId(10L)
                .deviceBindingEnabled(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private void stubRegularUser(Long id) {
        when(rbacService.getUserRoleCodes(id)).thenReturn(List.of("user"));
        when(rbacService.getUserPermissionCodes(id)).thenReturn(List.of("app:read"));
    }

    private void stubOperator(Long id) {
        when(rbacService.getUserRoleCodes(id)).thenReturn(List.of("super_admin"));
        when(sysUserMapper.selectOneById(id)).thenReturn(
                SysUser.builder().id(id).username("admin").build());
    }

    private void stubUserExists(SysUser u) {
        when(sysUserMapper.selectOneById(u.getId())).thenReturn(u);
    }

    @SuppressWarnings("unchecked")
    private void withUserUpdateChain(Runnable action) {
        UpdateChain<SysUser> chain = mock(UpdateChain.class, RETURNS_DEEP_STUBS);
        when(chain.update()).thenReturn(true);
        try (MockedStatic<UpdateChain> updateChain = mockStatic(UpdateChain.class)) {
            updateChain.when(() -> UpdateChain.of(SysUser.class)).thenReturn(chain);
            action.run();
        }
    }

    @Nested
    @DisplayName("查询")
    class Query {
        @Test
        @DisplayName("list 关键词与状态筛选")
        void list_withFilters() {
            SysUser u = user(USER_ID, 1);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(u));
            when(sysDeptMapper.selectOneById(10L)).thenReturn(SysDept.builder().id(10L).name("Eng").build());

            AdminUserQueryDTO q = new AdminUserQueryDTO();
            q.setPage(1);
            q.setSize(20);
            q.setKeyword("Nick");
            q.setStatus(1);
            var page = service.list(q);
            assertEquals(1, page.getTotal());
            assertEquals("Nick100", page.getItems().get(0).getNickname());
            assertEquals("Eng", page.getItems().get(0).getDeptName());
        }

        @Test
        @DisplayName("listForExport 导出列表")
        void listForExport() {
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(USER_ID, 1)));
            AdminUserQueryDTO q = new AdminUserQueryDTO();
            q.setKeyword("user");
            q.setStatus(1);
            assertEquals(1, service.listForExport(q).size());
        }

        @Test
        @DisplayName("detail 成功")
        void detail_found() {
            stubUserExists(user(USER_ID, 1));
            when(sysDeptMapper.selectOneById(10L)).thenReturn(SysDept.builder().id(10L).name("Eng").build());
            AdminUserDetailVO vo = service.detail(USER_ID);
            assertEquals(USER_ID, vo.getId());
            assertEquals("Eng", vo.getDeptName());
            assertFalse(Boolean.TRUE.equals(vo.getDeviceBindingEnabled()));
        }

        @Test
        @DisplayName("detail 404")
        void detail_notFound() {
            when(sysUserMapper.selectOneById(999L)).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.detail(999L));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("detail 数据权限外 404")
        void detail_outOfScope() {
            DataScopeContext.setAllowedUserIds(Set.of(1L));
            CustomException ex = assertThrows(CustomException.class, () -> service.detail(USER_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("devices 列表与在线标记")
        void devices_list() {
            stubUserExists(user(USER_ID, 1));
            DeviceVO dev = DeviceVO.builder().id("dev-1").deviceName("Chrome").build();
            when(deviceSessionService.listByUser(USER_ID, null)).thenReturn(List.of(dev));
            when(presenceService.onlineDeviceIds(USER_ID)).thenReturn(Set.of("dev-1"));
            when(deviceBanMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(deviceBindingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUserDeviceBinding.builder().deviceId("dev-1").build()
            ));

            List<DeviceVO> devices = service.devices(USER_ID);
            assertEquals(1, devices.size());
            assertTrue(devices.get(0).isOnline());
            assertTrue(devices.get(0).isApproved());
        }

        @Test
        @DisplayName("logins 分页")
        void logins_pagination() {
            stubUserExists(user(USER_ID, 1));
            SysLoginAudit log = SysLoginAudit.builder()
                    .id(1L).userId(USER_ID).username("user100").ip("127.0.0.1")
                    .userAgent("JUnit").success(1).createTime(new Date()).build();
            when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(sysLoginAuditMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(log));
            when(ipGeoService.resolve("127.0.0.1")).thenReturn("本地");

            AdminPageQueryDTO q = new AdminPageQueryDTO();
            q.setPage(1);
            q.setSize(10);
            q.setStatus(1);
            var page = service.logins(USER_ID, q);
            assertEquals(1, page.getTotal());
            assertEquals("本地", page.getItems().get(0).getRegion());
        }
    }

    @Nested
    @DisplayName("资料更新")
    class Update {
        @Test
        @DisplayName("update 昵称与邮箱")
        void update_success() {
            SysUser u = user(USER_ID, 1);
            stubUserExists(u);
            AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
            dto.setNickname("  NewNick  ");
            dto.setEmail("new@test.com");
            service.update(USER_ID, dto, OPERATOR_ID);
            assertEquals("NewNick", u.getNickname());
            assertEquals("new@test.com", u.getEmail());
            verify(sysUserMapper).update(u);
        }
    }

    @Nested
    @DisplayName("状态变更")
    class StatusOps {
        @Test
        @DisplayName("freeze 成功并吊销令牌")
        void freeze_success() {
            stubUserExists(user(USER_ID, 1));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(false);
            withUserUpdateChain(() -> service.freeze(USER_ID, new AdminUserActionDTO(), OPERATOR_ID));
            verify(tokenService).revokeAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("freeze 已冻结拒绝")
        void freeze_alreadyFrozen() {
            stubUserExists(user(USER_ID, 0));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(false);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.freeze(USER_ID, new AdminUserActionDTO(), OPERATOR_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("unfreeze 成功")
        void unfreeze_success() {
            stubUserExists(user(USER_ID, 0));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(false);
            assertDoesNotThrow(() -> withUserUpdateChain(() -> service.unfreeze(USER_ID, OPERATOR_ID)));
        }

        @Test
        @DisplayName("unfreeze 非冻结拒绝")
        void unfreeze_notFrozen() {
            stubUserExists(user(USER_ID, 1));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(false);
            assertThrows(CustomException.class, () -> service.unfreeze(USER_ID, OPERATOR_ID));
        }

        @Test
        @DisplayName("ban 成功")
        void ban_success() {
            stubUserExists(user(USER_ID, 1));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(false);
            AdminUserActionDTO dto = new AdminUserActionDTO();
            dto.setReason("spam");
            withUserUpdateChain(() -> service.ban(USER_ID, dto, OPERATOR_ID));
            verify(tokenService).revokeAllUserTokens(USER_ID);
            verify(deviceSessionService).deleteAllByUser(USER_ID);
            verify(adminBlacklistService).recordBan(USER_ID, "spam", OPERATOR_ID);
        }

        @Test
        @DisplayName("unban 成功")
        void unban_success() {
            stubUserExists(user(USER_ID, 0));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(true);
            withUserUpdateChain(() -> service.unban(USER_ID, OPERATOR_ID));
            verify(adminBlacklistService).releaseByUserId(USER_ID, null, OPERATOR_ID);
        }

        @Test
        @DisplayName("unban 未封禁拒绝")
        void unban_notBanned() {
            stubUserExists(user(USER_ID, 1));
            when(adminBlacklistService.hasActiveBan(USER_ID)).thenReturn(false);
            assertThrows(CustomException.class, () -> service.unban(USER_ID, OPERATOR_ID));
        }
    }

    @Nested
    @DisplayName("密码重置")
    class ResetPassword {
        @BeforeEach
        void initTx() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @Test
        @DisplayName("resetPassword 返回生成密码")
        void resetPassword_generated() {
            SysUser u = user(USER_ID, 1);
            stubUserExists(u);
            AdminUserResetPasswordVO vo = service.resetPassword(USER_ID, null, OPERATOR_ID);
            assertTrue(vo.isGenerated());
            assertNotNull(vo.getTemporaryPassword());
            assertFalse(vo.getTemporaryPassword().isBlank());
            verify(passwordPolicyService).validate(vo.getTemporaryPassword());
            verify(sysUserMapper).update(u);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(tokenService).revokeAllUserTokens(USER_ID);
            verify(deviceSessionService).deleteAllByUser(USER_ID);
        }

        @Test
        @DisplayName("resetPassword 指定密码")
        void resetPassword_custom() {
            SysUser u = user(USER_ID, 1);
            stubUserExists(u);
            AdminUserResetPasswordDTO dto = new AdminUserResetPasswordDTO();
            dto.setNewPassword("CustomPass1!");
            AdminUserResetPasswordVO vo = service.resetPassword(USER_ID, dto, OPERATOR_ID);
            assertFalse(vo.isGenerated());
            assertNull(vo.getTemporaryPassword());
            verify(passwordPolicyService).validate("CustomPass1!");
        }
    }

    @Nested
    @DisplayName("设备绑定")
    class DeviceBinding {
        @Test
        @DisplayName("setDeviceBindingEnabled 开启")
        void setDeviceBindingEnabled_on() {
            SysUser u = user(USER_ID, 1);
            u.setDeviceBindingEnabled(0);
            stubUserExists(u);
            when(deviceSessionService.listByUser(USER_ID, null)).thenReturn(List.of(
                    DeviceVO.builder().id("d1").deviceName("Phone").build()
            ));
            when(deviceBindingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            withUserUpdateChain(() ->
                    service.setDeviceBindingEnabled(USER_ID, true, OPERATOR_ID, "127.0.0.1", "JUnit"));
            verify(deviceBindingMapper).insert(any(SysUserDeviceBinding.class));
            verify(auditLogService).logWithTarget(any(), eq("启用设备强绑定"), eq(OPERATOR_ID), any(),
                    eq(USER_ID), any(), any(), any(), any(), any(), eq(true), isNull());
        }

        @Test
        @DisplayName("setDeviceBindingEnabled 已是目标状态则跳过")
        void setDeviceBindingEnabled_noop() {
            SysUser u = user(USER_ID, 1);
            u.setDeviceBindingEnabled(1);
            stubUserExists(u);
            service.setDeviceBindingEnabled(USER_ID, true, OPERATOR_ID, "127.0.0.1", "JUnit");
            verify(auditLogService, never()).logWithTarget(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("approveDevice 成功")
        void approveDevice_success() {
            SysUser u = user(USER_ID, 1);
            stubUserExists(u);
            when(deviceBindingMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            service.approveDevice(USER_ID, "dev-x", "My Phone", OPERATOR_ID, "127.0.0.1", "JUnit");
            verify(deviceBindingMapper).insert(any(SysUserDeviceBinding.class));
        }

        @Test
        @DisplayName("approveDevice 空 ID 拒绝")
        void approveDevice_emptyId() {
            stubUserExists(user(USER_ID, 1));
            assertThrows(CustomException.class,
                    () -> service.approveDevice(USER_ID, "  ", null, OPERATOR_ID, null, null));
        }

        @Test
        @DisplayName("revokeDeviceApproval 成功")
        void revokeDeviceApproval_success() {
            SysUser u = user(USER_ID, 1);
            u.setDeviceBindingEnabled(1);
            stubUserExists(u);
            SysUserDeviceBinding binding = SysUserDeviceBinding.builder()
                    .id(5L).userId(USER_ID).deviceId("dev-x").build();
            when(deviceBindingMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(binding);
            service.revokeDeviceApproval(USER_ID, "dev-x", OPERATOR_ID, "127.0.0.1", "JUnit");
            verify(deviceBindingMapper).deleteById(5L);
            verify(deviceSessionService).kickDevice(eq(USER_ID), eq("dev-x"), eq(OPERATOR_ID), any(), any(), any());
        }

        @Test
        @DisplayName("revokeDeviceApproval 不在白名单 404")
        void revokeDeviceApproval_notFound() {
            stubUserExists(user(USER_ID, 1));
            when(deviceBindingMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.revokeDeviceApproval(USER_ID, "missing", OPERATOR_ID, null, null));
            assertEquals(404, ex.getCode());
        }
    }
}
