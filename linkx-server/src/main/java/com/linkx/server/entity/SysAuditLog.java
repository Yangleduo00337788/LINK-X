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
        // P2: 多端设备管理
        DEVICE_LOGIN("设备上线"),
        DEVICE_KICK("踢设备下线"),
        DEVICE_KICK_ALL("踢所有设备下线"),
        DEVICE_BAN("封禁设备"),
        DEVICE_UNBAN("解封设备"),
        DEVICE_BINDING_TOGGLE("设备强绑定开关"),
        DEVICE_APPROVE("批准登录设备"),
        DEVICE_REVOKE("撤销登录设备"),
        // P2: 安全审计增强
        RECALL_MESSAGE("撤回消息"),
        EDIT_MESSAGE("编辑消息"),
        FORWARD_MESSAGE("转发消息"),
        MUTE_CONVERSATION("会话免打扰"),
        PIN_CONVERSATION("会话置顶"),
        // P2: 敏感词/黑名单
        BLACKLIST_ADD("加入黑名单"),
        BLACKLIST_REMOVE("移出黑名单"),
        SENSITIVE_WORD_MATCH("敏感词命中"),
        // P2: 音视频通话
        CALL_START("发起通话"),
        CALL_ACCEPT("接听通话"),
        CALL_HANGUP("挂断通话"),
        CALL_DEVICE_SWITCH("通话切换设备"),
        // P2: 消息风暴
        MESSAGE_STORM("消息风暴检测"),
        // P2: 数据合规
        DATA_EXPORT("数据导出"),
        DATA_PURGE("数据清除"),
        DATA_RETENTION("数据留存清理"),
        // P1-D8: RBAC 操作审计
        ROLE_GRANT("授予角色"),
        ROLE_REVOKE("撤销角色"),
        // 管理端系统配置
        UPDATE_SETTINGS("更新系统配置"),
        // 管理端内容治理
        CONTENT_REVIEW("内容审核"),
        RISK_EVENT_HANDLE("风险事件处置"),
        RISK_EVENT_BATCH("批量处置风险事件"),
        SENSITIVE_WORD_UPDATE("敏感词变更"),
        // 管理端运营
        NOTICE_UPDATE("公告变更"),
        NOTICE_PUBLISH("公告发布"),
        BANNER_UPDATE("Banner变更"),
        BANNER_PUBLISH("Banner发布"),
        RECOMMEND_UPDATE("推荐位变更"),
        RECOMMEND_PUBLISH("推荐位发布"),
        ACTIVITY_UPDATE("活动变更"),
        ACTIVITY_PUBLISH("活动发布"),
        VERSION_UPDATE("版本变更"),
        VERSION_PUBLISH("版本发布"),
        FEEDBACK_ESCALATE("反馈超时升级"),
        REVIEW_ESCALATE("审核超时督办"),
        AEGIS_CAPTURE("灵犀盾事件捕获"),
        AEGIS_ESCALATE("灵犀盾超时升级"),
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
