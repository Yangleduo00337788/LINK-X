package com.linkx.server.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.RbacConstants;
import com.linkx.server.entity.SysPermission;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.SysRolePermission;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.entity.admin.SysApprovalTempGrant;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysPermissionMapper;
import com.linkx.server.mapper.SysRoleMapper;
import com.linkx.server.mapper.SysRolePermissionMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.SysApprovalTempGrantMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.RbacService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 角色权限服务实现。
 * <p>
 * 基于 MyBatis-Flex QueryWrapper 进行分步查询（用户-角色-权限三层模型），
 * 角色码与权限码查询结果通过 Redis（StringRedisTemplate + JSON）缓存加速，
 * 角色分配/移除后主动清除对应用户的缓存，保证一致性。
 * </p>
 * <p>
 * 关联表 sys_user_role / sys_role_permission 采用物理删除，
 * 以避免与 uk_user_role / uk_role_permission 唯一索引冲突（删除后再次分配需可插入）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysApprovalTempGrantMapper approvalTempGrantMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Override
    public List<SysRole> getUserRoles(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> roleIds = getRoleIdsByUser(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysRole::getId).in(roleIds)
                        .and(SysRole::getStatus).eq(1)
        );
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        String key = RbacConstants.CACHE_KEY_ROLES + userId;
        List<String> cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        List<String> codes = getUserRoles(userId).stream()
                .map(SysRole::getRoleCode)
                .toList();
        writeCache(key, codes);
        return codes;
    }

    @Override
    public List<SysPermission> getUserPermissions(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> roleIds = getRoleIdsByUser(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = sysRolePermissionMapper.selectListByQuery(
                        QueryWrapper.create().where(SysRolePermission::getRoleId).in(roleIds))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return sysPermissionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysPermission::getId).in(permissionIds)
                        .and(SysPermission::getStatus).eq(1)
        );
    }

    @Override
    public List<String> getUserPermissionCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        String key = RbacConstants.CACHE_KEY_PERMS + userId;
        List<String> cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        List<String> codes = getUserPermissions(userId).stream()
                .map(SysPermission::getPermissionCode)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        codes.addAll(activeTempPermissionCodes(userId));
        List<String> merged = codes.stream().distinct().toList();
        writeCache(key, merged);
        return merged;
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        return getUserRoleCodes(userId).contains(roleCode);
    }

    @Override
    public boolean hasPermission(Long userId, String permCode) {
        if (userId == null || permCode == null) {
            return false;
        }
        List<String> perms = getUserPermissionCodes(userId);
        return perms.contains(permCode) || perms.contains(RbacConstants.PERM_ALL);
    }

    @Override
    @Transactional
    public SysRole createRole(String roleCode, String roleName, String description, Long createBy) {
        SysRole existing = sysRoleMapper.selectOneByQuery(
                QueryWrapper.create().where(SysRole::getRoleCode).eq(roleCode));
        if (existing != null) {
            throw new CustomException(400, "角色编码已存在: " + roleCode);
        }
        SysRole role = SysRole.builder()
                .roleCode(roleCode)
                .roleName(roleName)
                .description(description)
                .status(1)
                .dataScope(1)
                .createBy(createBy)
                .updateBy(createBy)
                .build();
        sysRoleMapper.insert(role);
        return role;
    }

    @Override
    public List<SysRole> listAllRoles() {
        return sysRoleMapper.selectListByQuery(
                QueryWrapper.create().where(SysRole::getStatus).eq(1));
    }

    @Override
    public SysRole getRoleById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return sysRoleMapper.selectOneById(roleId);
    }

    @Override
    @Transactional
    public void grantRole(Long userId, String roleCode, Long createBy) {
        if (userId == null || roleCode == null) {
            throw new CustomException(400, "参数不能为空");
        }
        SysRole role = sysRoleMapper.selectOneByQuery(
                QueryWrapper.create().where(SysRole::getRoleCode).eq(roleCode));
        if (role == null) {
            throw new CustomException(404, "角色不存在: " + roleCode);
        }
        grantRoleById(userId, role.getId(), createBy);
    }

    @Override
    @Transactional
    public void revokeRole(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return;
        }
        SysRole role = sysRoleMapper.selectOneByQuery(
                QueryWrapper.create().where(SysRole::getRoleCode).eq(roleCode));
        if (role == null) {
            return;
        }
        revokeRoleById(userId, role.getId());
    }

    @Override
    @Transactional
    public void grantRoleById(Long userId, Long roleId, Long createBy) {
        if (userId == null || roleId == null) {
            throw new CustomException(400, "参数不能为空");
        }
        // 内置高权角色保护：仅可由初始化脚本直接写 DB，禁止接口提权
        SysRole role = sysRoleMapper.selectOneById(roleId);
        if (role == null) {
            throw new CustomException(404, "角色不存在");
        }
        if (RbacConstants.ROLE_SUPER_ADMIN.equals(role.getRoleCode())
                || RbacConstants.ROLE_ADMIN.equals(role.getRoleCode())) {
            throw new CustomException(403, "管理员角色不可通过接口授予");
        }

        SysUserRole userRole = SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .createBy(createBy)
                .deleted(0)
                .createTime(new java.util.Date())
                .build();
        // catch 唯一索引冲突实现幂等（替代 check-then-insert 竞态）
        try {
            sysUserRoleMapper.insert(userRole);
            // 审计：高敏操作落审计日志，便于溯源
            auditLogService.log(
                    com.linkx.server.entity.SysAuditLog.OperationType.ROLE_GRANT,
                    "授权角色: userId=" + userId + ", roleCode=" + role.getRoleCode(),
                    createBy, null, null, null, true, null);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 已存在有效分配，静默返回（幂等）
        }
        evictUserCache(userId);
    }

    @Override
    @Transactional
    public void revokeRoleById(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return;
        }
        // 内置高权角色保护：禁止撤销管理员/超管
        SysRole role = sysRoleMapper.selectOneById(roleId);
        if (role != null && (RbacConstants.ROLE_SUPER_ADMIN.equals(role.getRoleCode())
                || RbacConstants.ROLE_ADMIN.equals(role.getRoleCode()))) {
            throw new CustomException(403, "管理员角色不可通过接口撤销");
        }
        int affected = sysUserRoleMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(SysUserRole::getUserId).eq(userId)
                        .and(SysUserRole::getRoleId).eq(roleId));
        if (affected > 0) {
            auditLogService.log(
                    com.linkx.server.entity.SysAuditLog.OperationType.ROLE_REVOKE,
                    "撤销角色: userId=" + userId + ", roleCode=" + (role != null ? role.getRoleCode() : String.valueOf(roleId)),
                    null, null, null, null, true, null);
        }
        evictUserCache(userId);
    }

    @Override
    public void evictUserCache(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(RbacConstants.CACHE_KEY_ROLES + userId);
            redisTemplate.delete(RbacConstants.CACHE_KEY_PERMS + userId);
        } catch (Exception e) {
            // Redis 异常不影响 DB 事务，缓存会在 TTL 后自然失效
            log.warn("清除用户 RBAC 缓存失败 userId={}, err={}", userId, e.getMessage());
        }
    }

    @Override
    public void evictAllUserCaches() {
        List<Long> userIds = sysUserRoleMapper.selectListByQuery(QueryWrapper.create())
                .stream()
                .map(SysUserRole::getUserId)
                .distinct()
                .toList();
        for (Long userId : userIds) {
            evictUserCache(userId);
        }
        log.info("已清除 {} 个用户的 RBAC 缓存", userIds.size());
    }

    private List<String> activeTempPermissionCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return approvalTempGrantMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(SysApprovalTempGrant::getUserId).eq(userId)
                                .and(SysApprovalTempGrant::getRevokedAt).isNull())
                .stream()
                .map(SysApprovalTempGrant::getPermissionCode)
                .distinct()
                .toList();
    }

    /**
     * 查询用户关联的角色 ID 集合（去重）。
     */
    private List<Long> getRoleIdsByUser(Long userId) {
        return sysUserRoleMapper.selectListByQuery(
                        QueryWrapper.create().where(SysUserRole::getUserId).eq(userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
    }

    /**
     * 读取角色/权限编码缓存，反序列化为 List<String>；缓存不存在或反序列化失败返回 null。
     */
    private List<String> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("读取 RBAC 缓存失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写入角色/权限编码缓存，TTL 30 分钟；写入失败仅告警不影响主流程。
     */
    private void writeCache(String key, List<String> codes) {
        try {
            redisTemplate.opsForValue().set(key,
                    objectMapper.writeValueAsString(codes),
                    Duration.ofMinutes(RbacConstants.CACHE_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("写入 RBAC 缓存失败 key={}, err={}", key, e.getMessage());
        }
    }
}
