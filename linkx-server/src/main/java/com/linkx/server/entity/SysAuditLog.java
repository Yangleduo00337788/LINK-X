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

/**
 * 敏感操作审计日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_audit_log")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        LOGIN("登录"),
        LOGOUT("登出"),
        REGISTER("注册"),
        RESET_PASSWORD("重置密码"),
        CHANGE_PASSWORD("修改密码"),
        UPDATE_PROFILE("更新资料"),
        UPDATE_AVATAR("更新头像"),
        SEND_MESSAGE("发送消息"),
        CREATE_GROUP("创建群聊"),
        JOIN_GROUP("加入群聊"),
        LEAVE_GROUP("离开群聊"),
        DELETE_FRIEND("删除好友"),
        UPLOAD_FILE("上传文件"),
        RED_PACKET("红包操作"),
        ;

        private final String description;

        OperationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 操作类型 */
    private String operationType;

    /** 操作描述 */
    private String description;

    /** 操作者用户ID */
    private Long userId;

    /** 操作者用户名 */
    private String username;

    /** 目标用户ID（如修改密码、删除好友等操作的受影响用户） */
    private Long targetUserId;

    /** 目标用户名 */
    private String targetUsername;

    /** 目标资源ID（如会话ID、群ID等） */
    private String targetResourceId;

    /** 目标资源类型 */
    private String targetResourceType;

    /** 客户端IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 操作状态：SUCCESS/FAIL */
    private String status;

    /** 失败原因 */
    private String failureReason;

    /** 额外数据（JSON格式） */
    private String extraData;

    /** 创建时间 */
    private Date createTime;
}
