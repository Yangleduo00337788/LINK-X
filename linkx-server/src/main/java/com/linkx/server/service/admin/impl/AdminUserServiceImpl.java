package com.linkx.server.service.admin.impl;

import com.linkx.server.common.DataScope;
import com.linkx.server.common.DataScopeContext;
import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserResetPasswordDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.admin.vo.AdminUserResetPasswordVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysDept;
import com.linkx.server.entity.SysDeviceBan;
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
import com.linkx.server.service.admin.AdminBlacklistService;
import com.linkx.server.service.admin.AdminUserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*".toCharArray();

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final SysDeviceBanMapper deviceBanMapper;
    private final SysUserDeviceBindingMapper deviceBindingMapper;
    private final RbacService rbacService;
    private final DeviceSessionService deviceSessionService;
    private final PresenceService presenceService;
    private final TokenService tokenService;
    private final AdminBlacklistService adminBlacklistService;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditLogService auditLogService;
    private final IpGeoService ipGeoService;
    private final LinkxProperties linkxProperties;

    @Override
    @DataScope
    public PageResultVO<AdminUserListVO> list(AdminUserQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildListQuery(query);
        qw.orderBy(SysUser::getCreateTime, false);
        long total = sysUserMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminUserListVO> items = sysUserMapper.selectListByQuery(qw).stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    @DataScope
    public List<AdminUserListVO> listForExport(AdminUserQueryDTO query) {
        QueryWrapper qw = buildListQuery(query);
        qw.orderBy(SysUser::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        return sysUserMapper.selectListByQuery(qw).stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
    }

    private QueryWrapper buildListQuery(AdminUserQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        applyDataScopeUserFilter(qw);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysUser::getUsername).like(kw)
                        .or(SysUser::getNickname).like(kw)
                        .or(SysUser::getEmail).like(kw)
                        .or(SysUser::getPhone).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysUser::getStatus).eq(query.getStatus());
        }
        if (query.getDeptId() != null) {
            qw.and(SysUser::getDeptId).eq(query.getDeptId());
        }
        if (query.getStartTime() != null) {
            qw.and(SysUser::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysUser::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    @Override
    @DataScope
    public AdminUserDetailVO detail(Long id) {
        SysUser user = requireUserInScope(id);
        return AdminUserDetailVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .signature(user.getSignature())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .country(user.getCountry())
                .province(user.getProvince())
                .region(user.getRegion())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .deptId(user.getDeptId())
                .deptName(resolveDeptName(user.getDeptId()))
                .deviceBindingEnabled(Integer.valueOf(1).equals(user.getDeviceBindingEnabled()))
                .roles(rbacService.getUserRoleCodes(id))
                .permissions(rbacService.getUserPermissionCodes(id))
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    @Override
    @Transactional
    public void update(Long id, AdminUserUpdateDTO dto, Long operatorId) {
        assertCanModifyTarget(id, operatorId, false);
        SysUser user = requireUser(id);
        if (dto.getNickname() != null) {
            user.setNickname(InputSanitizer.sanitizeText(dto.getNickname(), 64));
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getSignature() != null) {
            user.setSignature(InputSanitizer.sanitizeText(dto.getSignature(), 255));
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        Long clearDeptId = null;
        if (dto.getDeptId() != null) {
            if (dto.getDeptId() == 0L) {
                clearDeptId = 0L;
            } else {
                SysDept dept = sysDeptMapper.selectOneById(dto.getDeptId());
                if (dept == null) {
                    throw new CustomException(400, "部门不存在");
                }
                user.setDeptId(dto.getDeptId());
            }
        }
        user.setUpdateBy(operatorId);
        user.setUpdateTime(new Date());
        sysUserMapper.update(user);
        if (clearDeptId != null) {
            UpdateChain.of(SysUser.class)
                    .set(SysUser::getDeptId, null)
                    .set(SysUser::getUpdateBy, operatorId)
                    .set(SysUser::getUpdateTime, new Date())
                    .where(SysUser::getId).eq(id)
                    .update();
        }
    }

    @Override
    @Transactional
    public void freeze(Long id, AdminUserActionDTO dto, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        SysUser user = requireUser(id);
        if (adminBlacklistService.hasActiveBan(id)) {
            throw new CustomException(400, "该用户已被封禁，无需冻结");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new CustomException(400, "该用户已被冻结，无需重复操作");
        }
        setStatus(id, 0, operatorId);
        tokenService.revokeAllUserTokens(id);
    }

    @Override
    @Transactional
    public void unfreeze(Long id, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        if (adminBlacklistService.hasActiveBan(id)) {
            throw new CustomException(400, "该用户为封禁状态，请使用解封而非解冻");
        }
        SysUser user = requireUser(id);
        if (user.getStatus() == null || user.getStatus() != 0) {
            throw new CustomException(400, "该用户当前不是冻结状态");
        }
        setStatus(id, 1, operatorId);
    }

    @Override
    @Transactional
    public void ban(Long id, AdminUserActionDTO dto, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        if (adminBlacklistService.hasActiveBan(id)) {
            throw new CustomException(400, "该用户已被封禁，无需重复操作");
        }
        setStatus(id, 0, operatorId);
        tokenService.revokeAllUserTokens(id);
        deviceSessionService.deleteAllByUser(id);
        String reason = dto == null ? null : dto.getReason();
        adminBlacklistService.recordBan(id, reason, operatorId);
    }

    @Override
    @Transactional
    public void unban(Long id, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        if (!adminBlacklistService.hasActiveBan(id)) {
            SysUser user = requireUser(id);
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new CustomException(400, "该用户为冻结状态，请使用解冻而非解封");
            }
            throw new CustomException(400, "该用户未被封禁");
        }
        setStatus(id, 1, operatorId);
        adminBlacklistService.releaseByUserId(id, null, operatorId);
    }

    @Override
    @Transactional
    public AdminUserResetPasswordVO resetPassword(Long id, AdminUserResetPasswordDTO dto, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        SysUser user = requireUser(id);

        boolean generated = dto == null || !StringUtils.hasText(dto.getNewPassword());
        String plain = generated ? generateTemporaryPassword() : dto.getNewPassword().trim();
        passwordPolicyService.validate(plain);

        user.setPassword(PasswordEncoderHolder.encode(plain));
        user.setUpdateBy(operatorId);
        user.setUpdateTime(new Date());
        sysUserMapper.update(user);

        Long userIdToRevoke = user.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tokenService.revokeAllUserTokens(userIdToRevoke);
                deviceSessionService.deleteAllByUser(userIdToRevoke);
            }
        });

        return AdminUserResetPasswordVO.builder()
                .generated(generated)
                .temporaryPassword(generated ? plain : null)
                .build();
    }

    @Override
    @DataScope
    public List<DeviceVO> devices(Long id) {
        requireUserInScope(id);
        Set<String> onlineDevices = presenceService.onlineDeviceIds(id);
        Set<String> bannedDevices = loadBannedDeviceIds(id);
        Set<String> approvedDevices = loadApprovedDeviceIds(id);
        return deviceSessionService.listByUser(id, null).stream()
                .peek(device -> {
                    String deviceId = device.getId();
                    device.setOnline(deviceId != null && onlineDevices.contains(deviceId));
                    device.setBanned(deviceId != null && bannedDevices.contains(deviceId));
                    device.setApproved(deviceId != null && approvedDevices.contains(deviceId));
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @DataScope
    public void setDeviceBindingEnabled(Long id, boolean enabled, Long operatorId, String ip, String userAgent) {
        assertCanModifyTarget(id, operatorId, true);
        SysUser user = requireUserInScope(id);
        boolean current = Integer.valueOf(1).equals(user.getDeviceBindingEnabled());
        if (current == enabled) {
            return;
        }
        Date now = new Date();
        UpdateChain.of(SysUser.class)
                .set(SysUser::getDeviceBindingEnabled, enabled ? 1 : 0)
                .set(SysUser::getUpdateBy, operatorId)
                .set(SysUser::getUpdateTime, now)
                .where(SysUser::getId).eq(id)
                .update();
        if (enabled) {
            approveCurrentSessions(id, operatorId, now);
        }
        String operator = resolveOperatorName(operatorId);
        auditLogService.logWithTarget(
                SysAuditLog.OperationType.DEVICE_BINDING_TOGGLE,
                enabled ? "启用设备强绑定" : "关闭设备强绑定",
                operatorId,
                operator,
                id,
                user.getUsername(),
                String.valueOf(id),
                "user",
                ip,
                userAgent,
                true,
                null
        );
    }

    @Override
    @Transactional
    @DataScope
    public void approveDevice(Long id, String deviceId, String deviceName, Long operatorId, String ip, String userAgent) {
        assertCanModifyTarget(id, operatorId, true);
        SysUser user = requireUserInScope(id);
        if (!StringUtils.hasText(deviceId)) {
            throw new CustomException(400, "设备ID不能为空");
        }
        String normalized = deviceId.trim();
        if (deviceBindingMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserDeviceBinding::getUserId).eq(id)
                        .and(SysUserDeviceBinding::getDeviceId).eq(normalized)) > 0) {
            throw new CustomException(400, "该设备已在白名单中");
        }
        Date now = new Date();
        String name = StringUtils.hasText(deviceName)
                ? InputSanitizer.sanitizeText(deviceName.trim(), 100)
                : null;
        deviceBindingMapper.insert(SysUserDeviceBinding.builder()
                .userId(id)
                .deviceId(normalized)
                .deviceName(name)
                .approvedBy(operatorId)
                .approvedAt(now)
                .createTime(now)
                .build());
        String operator = resolveOperatorName(operatorId);
        auditLogService.logWithTarget(
                SysAuditLog.OperationType.DEVICE_APPROVE,
                "批准登录设备: " + normalized,
                operatorId,
                operator,
                id,
                user.getUsername(),
                normalized,
                "device",
                ip,
                userAgent,
                true,
                null
        );
    }

    @Override
    @Transactional
    @DataScope
    public void revokeDeviceApproval(Long id, String deviceId, Long operatorId, String ip, String userAgent) {
        assertCanModifyTarget(id, operatorId, true);
        SysUser user = requireUserInScope(id);
        if (!StringUtils.hasText(deviceId)) {
            throw new CustomException(400, "设备ID不能为空");
        }
        String normalized = deviceId.trim();
        SysUserDeviceBinding binding = deviceBindingMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysUserDeviceBinding::getUserId).eq(id)
                        .and(SysUserDeviceBinding::getDeviceId).eq(normalized)
                        .limit(1)
        );
        if (binding == null) {
            throw new CustomException(404, "该设备不在白名单中");
        }
        deviceBindingMapper.deleteById(binding.getId());
        if (Integer.valueOf(1).equals(user.getDeviceBindingEnabled())) {
            String operator = resolveOperatorName(operatorId);
            deviceSessionService.kickDevice(id, normalized, operatorId, operator, ip, userAgent);
        }
        String operator = resolveOperatorName(operatorId);
        auditLogService.logWithTarget(
                SysAuditLog.OperationType.DEVICE_REVOKE,
                "撤销登录设备: " + normalized,
                operatorId,
                operator,
                id,
                user.getUsername(),
                normalized,
                "device",
                ip,
                userAgent,
                true,
                null
        );
    }

    private void approveCurrentSessions(Long userId, Long operatorId, Date now) {
        List<DeviceVO> sessions = deviceSessionService.listByUser(userId, null);
        Set<String> existing = loadApprovedDeviceIds(userId);
        for (DeviceVO session : sessions) {
            if (session.getId() == null || existing.contains(session.getId())) {
                continue;
            }
            deviceBindingMapper.insert(SysUserDeviceBinding.builder()
                    .userId(userId)
                    .deviceId(session.getId())
                    .deviceName(session.getDeviceName())
                    .approvedBy(operatorId)
                    .approvedAt(now)
                    .createTime(now)
                    .build());
        }
    }

    private Set<String> loadBannedDeviceIds(Long userId) {
        List<SysDeviceBan> bans = deviceBanMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysDeviceBan::getUserId).eq(userId)
                        .and(SysDeviceBan::getStatus).eq(SysDeviceBan.STATUS_ACTIVE)
        );
        return bans.stream().map(SysDeviceBan::getDeviceId).collect(Collectors.toSet());
    }

    private Set<String> loadApprovedDeviceIds(Long userId) {
        List<SysUserDeviceBinding> bindings = deviceBindingMapper.selectListByQuery(
                QueryWrapper.create().where(SysUserDeviceBinding::getUserId).eq(userId)
        );
        return bindings.stream().map(SysUserDeviceBinding::getDeviceId).collect(Collectors.toSet());
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return "admin";
        }
        SysUser operator = sysUserMapper.selectOneById(operatorId);
        if (operator != null && StringUtils.hasText(operator.getUsername())) {
            return operator.getUsername();
        }
        return String.valueOf(operatorId);
    }

    @Override
    @DataScope
    public PageResultVO<AdminLoginLogVO> logins(Long id, AdminPageQueryDTO query) {
        requireUserInScope(id);
        int page = normalizePage(query == null ? null : query.getPage());
        int size = normalizeSize(query == null ? null : query.getSize());
        QueryWrapper qw = QueryWrapper.create().where(SysLoginAudit::getUserId).eq(id);
        if (query != null && query.getStatus() != null) {
            qw.and(SysLoginAudit::getSuccess).eq(query.getStatus());
        }
        if (query != null && query.getStartTime() != null) {
            qw.and(SysLoginAudit::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query != null && query.getEndTime() != null) {
            qw.and(SysLoginAudit::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(SysLoginAudit::getCreateTime, false);
        long total = sysLoginAuditMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminLoginLogVO> items = sysLoginAuditMapper.selectListByQuery(qw).stream()
                .map(log -> {
                    String ip = ClientIpResolver.normalizeToIpv4(log.getIp());
                    return AdminLoginLogVO.builder()
                            .id(log.getId())
                            .userId(log.getUserId())
                            .username(log.getUsername())
                            .ip(ip)
                            .region(ipGeoService.resolve(ip))
                            .userAgent(log.getUserAgent())
                            .success(log.getSuccess())
                            .reason(log.getReason())
                            .createTime(log.getCreateTime())
                            .build();
                })
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    private String generateTemporaryPassword() {
        int minLen = Math.max(10, linkxProperties.getAuth().getPasswordMinLength());
        int len = Math.min(16, Math.max(minLen, linkxProperties.getAuth().getPasswordMaxLength()));
        char[] chars = new char[len];
        // 保证至少各类字符各一，满足常见策略
        chars[0] = "ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(SECURE_RANDOM.nextInt(23));
        chars[1] = "abcdefghijkmnopqrstuvwxyz".charAt(SECURE_RANDOM.nextInt(23));
        chars[2] = "23456789".charAt(SECURE_RANDOM.nextInt(8));
        chars[3] = "!@#$%^&*".charAt(SECURE_RANDOM.nextInt(8));
        for (int i = 4; i < len; i++) {
            chars[i] = PASSWORD_ALPHABET[SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length)];
        }
        for (int i = len - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private void setStatus(Long id, int status, Long operatorId) {
        requireUser(id);
        // 人工启停清除自动封禁标记，避免到期误解封/误保留
        UpdateChain.of(SysUser.class)
                .set(SysUser::getStatus, status)
                .set(SysUser::getAutoLockedUntil, null)
                .set(SysUser::getUpdateBy, operatorId)
                .set(SysUser::getUpdateTime, new Date())
                .where(SysUser::getId).eq(id)
                .update();
    }

    private AdminUserListVO toListVO(SysUser user) {
        return AdminUserListVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .deptId(user.getDeptId())
                .deptName(resolveDeptName(user.getDeptId()))
                .roles(rbacService.getUserRoleCodes(user.getId()))
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDept dept = sysDeptMapper.selectOneById(deptId);
        return dept == null ? null : dept.getName();
    }

    private void applyDataScopeUserFilter(QueryWrapper qw) {
        var allowed = DataScopeContext.getAllowedUserIds();
        if (allowed == null) {
            return;
        }
        if (allowed.isEmpty()) {
            qw.and(SysUser::getId).eq(-1L);
            return;
        }
        qw.and(SysUser::getId).in(allowed);
    }

    private SysUser requireUserInScope(Long id) {
        if (!DataScopeContext.allows(id)) {
            throw new CustomException(404, "user not found");
        }
        return requireUser(id);
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectOneById(id);
        if (user == null) {
            throw new CustomException(404, "user not found");
        }
        return user;
    }

    /**
     * @param statusAction true 表示冻/封/重置密码等状态变更（禁止自操作与操作管理员）；
     *                     false 表示资料编辑（允许改自己，禁止改其他管理员）
     */
    private void assertCanModifyTarget(Long targetUserId, Long operatorId, boolean statusAction) {
        if (targetUserId == null || operatorId == null) {
            throw new CustomException(400, "参数不能为空");
        }
        requireUser(targetUserId);
        if (statusAction && targetUserId.equals(operatorId)) {
            throw new CustomException(403, "不能对自己执行该操作");
        }
        if (isAdminUser(targetUserId) && (statusAction || !targetUserId.equals(operatorId))) {
            throw new CustomException(403, "不能对管理员账号执行该操作");
        }
    }

    private boolean isAdminUser(Long userId) {
        return AdminConstants.hasAdminPortalRole(rbacService.getUserRoleCodes(userId));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
