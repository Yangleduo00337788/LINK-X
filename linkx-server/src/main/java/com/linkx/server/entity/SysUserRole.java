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
 * 用户-角色关联实体，对应数据库表 sys_user_role。
 * <p>
 * 维护用户与角色的多对多关系，支持一个用户拥有多个角色。
 * 联合唯一索引 uk_user_role(user_id, role_id) 保证同一分配关系不重复。
 * </p>
 * <p>
 * 关联表采用物理删除（不标注逻辑删除注解），避免删除后再次分配时
 * 与唯一索引 uk_user_role 冲突；deleted 字段保留以满足审计规范，恒为 0。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_user_role")
public class SysUserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    // 主键 ID，使用雪花算法自动生成
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    // 用户 ID，关联 sys_user.id
    private Long userId;

    // 角色 ID，关联 sys_role.id
    private Long roleId;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    // 创建人 ID，审计字段（操作分配的管理员）
    private Long createBy;

    // 逻辑删除标记字段（关联表采用物理删除，恒为 0，保留以满足审计规范）
    @Column(onInsertValue = "0")
    @Builder.Default
    private Integer deleted = 0;
}
