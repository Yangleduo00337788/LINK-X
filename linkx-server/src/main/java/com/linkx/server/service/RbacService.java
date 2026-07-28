package com.linkx.server.service;

import com.linkx.server.entity.SysPermission;
import com.linkx.server.entity.SysRole;

import java.util.List;

/**
 * RBAC 角色权限服务。
 * <p>
 * 提供用户-角色-权限三层模型的查询与维护能力，
 * 角色码与权限码查询结果通过 Redis 缓存加速，角色变更时主动清除缓存。
 * </p>
 */
public interface RbacService {

    /**
     * 查询用户的角色列表（不经过缓存，直接查库，返回完整实体）。
     *
     * @param userId 用户 ID
     * @return 启用状态的角色集合，userId 为空时返回空列表
     */
    List<SysRole> getUserRoles(Long userId);

    /**
     * 查询用户的角色编码集合（优先读 Redis 缓存，TTL 30 分钟）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表，userId 为空时返回空列表
     */
    List<String> getUserRoleCodes(Long userId);

    /**
     * 查询用户的权限列表（不经过缓存，直接查库，返回完整实体）。
     *
     * @param userId 用户 ID
     * @return 启用状态的权限集合，userId 为空时返回空列表
     */
    List<SysPermission> getUserPermissions(Long userId);

    /**
     * 查询用户的权限编码集合（优先读 Redis 缓存，TTL 30 分钟）。
     *
     * @param userId 用户 ID
     * @return 权限编码列表，userId 为空时返回空列表
     */
    List<String> getUserPermissionCodes(Long userId);

    /**
     * 判断用户是否拥有指定角色。
     *
     * @param userId   用户 ID
     * @param roleCode 角色编码
     * @return true=拥有
     */
    boolean hasRole(Long userId, String roleCode);

    /**
     * 判断用户是否拥有指定权限（持有通配符 * 视为通过）。
     *
     * @param userId    用户 ID
     * @param permCode  权限编码
     * @return true=拥有
     */
    boolean hasPermission(Long userId, String permCode);

    /**
     * 创建角色（角色编码不可重复）。
     *
     * @param roleCode    角色编码
     * @param roleName    角色名称
     * @param description 角色描述
     * @param createBy    创建人 ID
     * @return 新建角色实体（含回填主键）
     */
    SysRole createRole(String roleCode, String roleName, String description, Long createBy);

    /**
     * 查询全部启用角色。
     *
     * @return 角色列表
     */
    List<SysRole> listAllRoles();

    /**
     * 根据角色 ID 查询角色。
     *
     * @param roleId 角色 ID
     * @return 角色实体，不存在返回 null
     */
    SysRole getRoleById(Long roleId);

    /**
     * 按角色编码为用户分配角色（幂等：已分配则直接返回）。
     *
     * @param userId   用户 ID
     * @param roleCode 角色编码
     * @param createBy 操作人 ID
     */
    void grantRole(Long userId, String roleCode, Long createBy);

    /**
     * 按角色编码移除用户角色（幂等：未分配则直接返回）。
     *
     * @param userId   用户 ID
     * @param roleCode 角色编码
     */
    void revokeRole(Long userId, String roleCode);

    /**
     * 按角色 ID 为用户分配角色（幂等：已分配则直接返回）。
     *
     * @param userId   用户 ID
     * @param roleId   角色 ID
     * @param createBy 操作人 ID
     */
    void grantRoleById(Long userId, Long roleId, Long createBy);

    /**
     * 按角色 ID 移除用户角色（幂等：未分配则直接返回）。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    void revokeRoleById(Long userId, Long roleId);

    /**
     * 清除指定用户的角色与权限缓存。
     * <p>
     * 在角色分配/移除后调用，保证后续查询拿到最新数据。
     * </p>
     *
     * @param userId 用户 ID
     */
    void evictUserCache(Long userId);
}
