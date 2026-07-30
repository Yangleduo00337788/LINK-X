package com.linkx.server.service.admin.impl;

import com.linkx.server.common.InputSanitizer;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.TokenService;
import com.linkx.server.service.admin.AdminUserService;
import com.mybatisflex.core.query.QueryWrapper;
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
    private final RbacService rbacService;
    private final DeviceSessionService deviceSessionService;
    private final TokenService tokenService;

    @Override
    public PageResultVO<AdminUserListVO> list(AdminUserQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
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
        qw.orderBy(SysUser::getCreateTime, false);
        long total = sysUserMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminUserListVO> items = sysUserMapper.selectListByQuery(qw).stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
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
        setStatus(id, 0, operatorId);
        tokenService.revokeAllUserTokens(id);
    }

    @Override
    @Transactional
    public void unfreeze(Long id, Long operatorId) {
        setStatus(id, 1, operatorId);
    }

    @Override
    @Transactional
    public void ban(Long id, AdminUserActionDTO dto, Long operatorId) {
        setStatus(id, 0, operatorId);
        tokenService.revokeAllUserTokens(id);
        deviceSessionService.deleteAllByUser(id);
    }

    @Override
    @Transactional
    public void unban(Long id, Long operatorId) {
        setStatus(id, 1, operatorId);
    }

    @Override
    public List<DeviceVO> devices(Long id) {
        requireUser(id);
        return deviceSessionService.listByUser(id, null);
    }

    private void setStatus(Long id, int status, Long operatorId) {
        SysUser user = requireUser(id);
        user.setStatus(status);
        user.setUpdateBy(operatorId);
        user.setUpdateTime(new Date());
        sysUserMapper.update(user);
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
