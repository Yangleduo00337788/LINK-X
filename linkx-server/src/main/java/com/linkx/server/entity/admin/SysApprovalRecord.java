package com.linkx.server.entity.admin;


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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_approval_record")
public class SysApprovalRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NODE_APPROVE = "approve";
    public static final String NODE_COUNTERSIGN = "countersign";
    public static final String NODE_CC = "cc";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_READ = "read";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long instanceId;
    private Integer stepIndex;
    private String stepName;
    private String nodeType;
    private Long assigneeId;
    private String assigneeName;
    private String status;
    private String comment;
    private Date actionTime;
    private Date createTime;
}
