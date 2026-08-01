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
@Table("sys_review_task")
public class SysReviewTask implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_REPORT = "report";
    public static final String SOURCE_SENSITIVE = "sensitive";
    public static final String SOURCE_MANUAL = "manual";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    /** 审核目标类型 */
    public static final String TARGET_USER = "user";
    public static final String TARGET_GROUP = "group";
    public static final String TARGET_MESSAGE = "message";
    public static final String TARGET_CONVERSATION = "conversation";
    public static final String TARGET_MOMENT = "moment";
    public static final String TARGET_MOMENT_COMMENT = "moment_comment";
    public static final String TARGET_ANNOUNCEMENT = "announcement";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String sourceType;
    private String targetType;
    private String targetId;
    private Long reporterUserId;
    private String reporterUsername;
    private String title;
    private String contentSnapshot;
    private String riskLevel;
    private String status;
    private Long feedbackId;
    private Long assigneeId;
    private String resolution;
    private Long resolvedBy;
    private Date resolvedAt;
    private Date createTime;
    private Date updateTime;
}
