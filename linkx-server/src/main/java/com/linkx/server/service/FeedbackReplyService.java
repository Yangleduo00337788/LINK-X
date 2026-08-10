package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.vo.FeedbackReplyVO;
import com.linkx.server.entity.Feedback;

import java.util.List;

public interface FeedbackReplyService {

    List<FeedbackReplyVO> listByFeedbackId(Long feedbackId);

    FeedbackReplyVO addAdminReply(Feedback feedback, String content, Long operatorId);

    FeedbackReplyVO addUserReply(Feedback feedback, Long userId, String username, String content);
}
