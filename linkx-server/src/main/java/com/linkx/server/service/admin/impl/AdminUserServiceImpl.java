package com.linkx.server.service.admin.impl;

import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.TokenService;
import com.linkx.server.service.admin.AdminBlacklistService;
import com.linkx.server.service.admin.AdminUserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserMapper sysUserMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final RbacService rbacService;
    private final DeviceSessionService deviceSessionService;
    private final TokenService tokenService;
    private final AdminBlacklistService adminBlacklistService;

    @Override
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
        if (query.getStartTime() != null) {
            qw.and(SysUser::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysUser::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    @Override
    public AdminUserDetailVO detail(Long id) {
        SysUser user = requireUser(id);
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
        user.setUpdateBy(operatorId);
        user.setUpdateTime(new Date());
        sysUserMapper.update(user);
    }

    @Override
    @Transactional
    public void freeze(Long id, AdminUserActionDTO dto, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        setStatus(id, 0, operatorId);
        tokenService.revokeAllUserTokens(id);
    }

    @Override
    @Transactional
    public void unfreeze(Long id, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
        setStatus(id, 1, operatorId);
    }

    @Override
    @Transactional
    public void ban(Long id, AdminUserActionDTO dto, Long operatorId) {
        assertCanModifyTarget(id, operatorId, true);
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
        setStatus(id, 1, operatorId);
        adminBlacklistService.releaseByUserId(id, null, operatorId);
    }

    @Override
    public List<DeviceVO> devices(Long id) {
        requireUser(id);
        return deviceSessionService.listByUser(id, null);
    }

    @Override
    public PageResultVO<AdminLoginLogVO> logins(Long id, AdminPageQueryDTO query) {
        requireUser(id);
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
                .map(log -> AdminLoginLogVO.builder()
                        .id(log.getId())
                        .userId(log.getUserId())
                        .username(log.getUsername())
                        .ip(ClientIpResolver.normalizeToIpv4(log.getIp()))
                        .userAgent(log.getUserAgent())
                        .success(log.getSuccess())
                        .reason(log.getReason())
                        .createTime(log.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
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
                .roles(rbacService.getUserRoleCodes(user.getId()))
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectOneById(id);
        if (user == null) {
            throw new CustomException(404, "user not found");
        }
        return user;
    }

    /**
     * @param statusAction true 表示冻/封等状态变更（禁止自操作与操作管理员）；
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
        List<String> roles = rbacService.getUserRoleCodes(userId);
        for (String required : AdminConstants.ADMIN_ROLES) {
            if (roles.contains(required)) {
                return true;
            }
        }
        return false;
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
