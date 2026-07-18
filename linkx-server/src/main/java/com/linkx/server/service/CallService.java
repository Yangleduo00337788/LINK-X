package com.linkx.server.service;

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
}
