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
@Table("sys_admin_statistic_snapshot")
public class SysAdminStatisticSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Date snapshotDate;
    private String metricDomain;
    private String metricKey;
    private String dimensionKey;
    private String dimensionValue;
    private Long metricValue;
    private String extraJson;
    private Date createTime;
}
