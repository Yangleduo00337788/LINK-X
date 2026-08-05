package com.linkx.server.entity.admin;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_risk_rule")
public class SysRiskRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String name;
    private String scope;
    private String keyword;
    private String conditionJson;
    private Integer scoreDelta;
    private String actionType;
    private String actionConfig;
    private Integer priority;
    private Boolean enabled;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
