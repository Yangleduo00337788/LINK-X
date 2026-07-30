package com.linkx.server.entity.admin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_admin_menu")
public class AdminMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long parentId;
    private String name;
    private String title;
    private String path;
    private String component;
    private String redirect;
    private String icon;
    private String menuType;
    private String permissionCode;
    private Integer sortOrder;
    private Integer hidden;
    private Integer cacheable;
    private Integer externalLink;
    private Integer keepAlive;
    private Integer status;
    private String remark;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
