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
 * 系统角色实体，对应数据库表 sys_role。
 * <p>
 * RBAC 角色权限体系中的角色主体，与用户通过 sys_user_role 多对多关联，
 * 与权限通过 sys_role_permission 多对多关联。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_role")
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    // 主键 ID，使用雪花算法自动生成
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    // 角色编码，业务唯一标识（如 admin / user），唯一索引 uk_role_code
    private String roleCode;

    // 角色名称，界面展示用
    private String roleName;

    // 角色描述，可为空
    private String description;

    // 状态：1=启用，0=停用
    private Integer status;

    /**
     * 数据权限范围：1=全部，2=仅本人，3=本部门及下级，4=自定义组织。
     * 见 {@link com.linkx.server.common.admin.DataScopeType}。
     */
    private Integer dataScope;

    // 创建时间，数据库默认 CURRENT_TIMESTAMP
    private Date createTime;

    // 更新时间，数据库 ON UPDATE CURRENT_TIMESTAMP
    private Date updateTime;

    // 创建人 ID，审计字段
    private Long createBy;

    // 修改人 ID，审计字段
    private Long updateBy;

    // 逻辑删除标记：0=未删除，1=已删除
    @Column(isLogicDelete = true)
    private Integer deleted;
}
