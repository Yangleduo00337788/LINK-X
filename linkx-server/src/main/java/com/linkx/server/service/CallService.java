package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallIdDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.vo.CallInviteVO;

/**
 * 语音/视频通话信令服务（邀请、接听、挂断与 WebRTC 中继）
 */
public interface CallService {

    CallInviteVO invite(Long userId, CallInviteDTO dto);

    void cancel(Long userId, CallCancelDTO dto);

    void accept(Long userId, CallIdDTO dto);

    void reject(Long userId, CallIdDTO dto);

    void hangup(Long userId, CallIdDTO dto);

    void signal(Long userId, CallSignalDTO dto);

    // ==================== 断线重连 ====================

    /** 客户端断线后尝试重连通话 */
    void reconnect(Long userId, String callId);

    // ==================== 设备切换 ====================

    /** 通话中切换设备（摄像头/麦克风） */
    void switchDevice(Long userId, String callId, String deviceType, boolean enabled);

    // ==================== 多人会议 ====================

    /** 创建多人会议 */
    String createConference(Long userId, Long conversationId, String callType);

    String createConference(Long userId, Long conversationId, String callType, Long conferenceId);

    /** 创建多人会议（带标题，用于邀请推送与落库通知） */
    String createConference(Long userId, Long conversationId, String callType, Long conferenceId, String title);

    /** 创建多人会议（含是否有密码，供邀请卡片展示） */
    String createConference(Long userId, Long conversationId, String callType, Long conferenceId, String title, boolean hasPassword);

    /** 创建多人会议（含场景 call/meeting） */
    String createConference(Long userId, Long conversationId, String callType, Long conferenceId, String title, boolean hasPassword, String scene);

    /** 加入多人会议 */
    void joinConference(Long userId, String callId);

    /** 离开多人会议 */
    void leaveConference(Long userId, String callId);

    /** 获取会议参与者列表（调用方须为会话成员或已入会） */
    java.util.List<java.util.Map<String, Object>> getConferenceParticipants(Long userId, String callId);
}
