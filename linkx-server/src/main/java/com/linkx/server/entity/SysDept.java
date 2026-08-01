package com.linkx.server.entity;

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
@Table("sys_dept")
public class SysDept implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private Long createBy;
    private Long updateBy;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
