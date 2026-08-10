package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.vo.FeedbackReplyVO;
import com.linkx.server.controller.vo.FeedbackVO;
import com.linkx.server.entity.Feedback;

import java.util.List;

public interface FeedbackService {

    Feedback create(Long userId, String username, String type, String content, String contact);

    List<Feedback> listByUser(Long userId);

    FeedbackVO getDetail(Long userId, Long feedbackId);

    List<FeedbackReplyVO> listReplies(Long userId, Long feedbackId);

    FeedbackReplyVO userReply(Long userId, String username, Long feedbackId, String content);
}
