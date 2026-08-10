package com.linkx.server.entity;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统权限实体，对应数据库表 sys_permission。
 * <p>
 * RBAC 权限主体，支持 menu / button / api 三类资源。
 * 权限编码 permission_code 为业务唯一标识，注解 @RequirePermission 直接比对编码。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_permission")
public class SysPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    // 主键 ID，使用雪花算法自动生成
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    // 权限编码，业务唯一标识（如 user:delete），唯一索引 uk_perm_code
    private String permissionCode;

    // 权限名称，界面展示用
    private String permissionName;

    // 资源类型：menu / button / api
    private String resourceType;

    // 资源路径（API 路径或菜单路径），可为空
    private String resourcePath;

    // 描述，可为空
    private String description;

    // 状态：1=启用，0=停用
    private Integer status;

    // 创建时间，数据库默认 CURRENT_TIMESTAMP
    private Date createTime;

    // 更新时间，数据库 ON UPDATE CURRENT_TIMESTAMP
    private Date updateTime;

    // 逻辑删除标记：0=未删除，1=已删除
    @Column(isLogicDelete = true)
    private Integer deleted;
}
