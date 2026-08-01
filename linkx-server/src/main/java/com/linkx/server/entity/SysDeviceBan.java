package com.linkx.server.entity;

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
@Table("sys_device_ban")
public class SysDeviceBan implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RELEASED = "released";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;
    private String deviceId;
    private String reason;
    private String status;
    private Long bannedBy;
    private Long releasedBy;
    private Date releasedAt;
    private Date createTime;
    private Date updateTime;
}
