package com.linkx.server.service;

import com.linkx.server.controller.dto.SendRedPacketDTO;
import com.linkx.server.controller.vo.RedPacketVO;

import java.util.List;

/**
 * 红包服务接口
 */
public interface RedPacketService {

    /**
     * 发送红包
     */
    RedPacketVO sendRedPacket(Long userId, SendRedPacketDTO dto);

    /**
     * 领取红包
     */
    RedPacketVO receiveRedPacket(Long userId, String redPacketId);

    /**
     * 获取红包详情
     */
    RedPacketVO getRedPacket(Long userId, String redPacketId);

    /**
     * 获取会话中的红包列表
     */
    List<RedPacketVO> listByConversation(Long userId, Long conversationId);

    /**
     * 过期红包处理（定时任务）
     */
    void expireRedPackets();
}
