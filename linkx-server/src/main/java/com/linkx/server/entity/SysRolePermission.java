package com.linkx.server.entity;


/**
 * 作者：yangleduo
 */
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
 * 角色-权限关联实体，对应数据库表 sys_role_permission。
 * <p>
 * 维护角色与权限的多对多关系，联合唯一索引 uk_role_permission(role_id, permission_id)。
 * </p>
 * <p>
 * 关联表采用物理删除（不标注逻辑删除注解），避免删除后再次分配时
 * 与唯一索引 uk_role_permission 冲突；deleted 字段保留以满足审计规范，恒为 0。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_role_permission")
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    // 主键 ID，使用雪花算法自动生成
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    // 角色 ID，关联 sys_role.id
    private Long roleId;

    // 权限 ID，关联 sys_permission.id
    private Long permissionId;

    // 创建时间，数据库默认 CURRENT_TIMESTAMP
    private Date createTime;

    // 创建人 ID，审计字段
    private Long createBy;

    // 逻辑删除标记字段（关联表采用物理删除，恒为 0，保留以满足审计规范）
    private Integer deleted;
}
