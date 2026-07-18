package com.linkx.server.service;

import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.vo.CallInviteVO;

/**
 * 语音/视频通话信令服务
 */
public interface CallService {

    CallInviteVO invite(Long userId, CallInviteDTO dto);

    void cancel(Long userId, CallCancelDTO dto);
}
