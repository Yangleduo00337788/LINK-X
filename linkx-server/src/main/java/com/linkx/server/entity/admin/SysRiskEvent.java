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
@Table("sys_risk_event")
public class SysRiskEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_SENSITIVE_WORD_MATCH = "SENSITIVE_WORD_MATCH";
    public static final String TYPE_MESSAGE_STORM = "MESSAGE_STORM";
    public static final String TYPE_LOGIN_LOCK = "LOGIN_LOCK";
    public static final String TYPE_RATE_LIMIT = "RATE_LIMIT";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_HANDLED = "handled";
    public static final String STATUS_IGNORED = "ignored";

    public static final String LEVEL_LOW = "low";
    public static final String LEVEL_MEDIUM = "medium";
    public static final String LEVEL_HIGH = "high";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String eventType;
    private String title;
    private String detail;
    private String riskLevel;
    private String status;
    private Long userId;
    private String username;
    private String targetResourceId;
    private String targetResourceType;
    private String ip;
    private String extraData;
    private Long auditLogId;
    private String resolution;
    private Long handledBy;
    private Date handledAt;
    private Date createTime;
    private Date updateTime;
}
