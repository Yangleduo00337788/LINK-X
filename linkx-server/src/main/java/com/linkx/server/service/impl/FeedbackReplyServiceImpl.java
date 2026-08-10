package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.vo.FeedbackReplyVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysFeedbackReply;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysFeedbackReplyMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.FeedbackReplyService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackReplyServiceImpl implements FeedbackReplyService {

    private static final String OFFICIAL_NAME = "LinkX\u5B98\u65B9";

    private final SysFeedbackReplyMapper replyMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<FeedbackReplyVO> listByFeedbackId(Long feedbackId) {
        if (feedbackId == null) {
            return List.of();
        }
        return replyMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(SysFeedbackReply::getFeedbackId).eq(feedbackId)
                                .orderBy(SysFeedbackReply::getCreateTime, true))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public FeedbackReplyVO addAdminReply(Feedback feedback, String content, Long operatorId) {
        String text = requireContent(content);
        String senderName = resolveAdminName(operatorId);
        SysFeedbackReply row = SysFeedbackReply.builder()
                .feedbackId(feedback.getId())
                .senderType(SysFeedbackReply.SENDER_ADMIN)
                .senderId(operatorId)
                .senderName(senderName)
                .content(text)
                .build();
        replyMapper.insert(row);
        return toVO(row);
    }

    @Override
    public FeedbackReplyVO addUserReply(Feedback feedback, Long userId, String username, String content) {
        String text = requireContent(content);
        SysFeedbackReply row = SysFeedbackReply.builder()
                .feedbackId(feedback.getId())
                .senderType(SysFeedbackReply.SENDER_USER)
                .senderId(userId)
                .senderName(StringUtils.hasText(username) ? username.trim() : "user")
                .content(text)
                .build();
        replyMapper.insert(row);
        return toVO(row);
    }

    private static String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new CustomException(400, "content required");
        }
        return content.trim();
    }

    private String resolveAdminName(Long operatorId) {
        if (operatorId == null) {
            return OFFICIAL_NAME;
        }
        SysUser user = sysUserMapper.selectOneById(operatorId);
        if (user == null) {
            return OFFICIAL_NAME;
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return OFFICIAL_NAME;
    }

    private FeedbackReplyVO toVO(SysFeedbackReply row) {
        return FeedbackReplyVO.builder()
                .id(row.getId())
                .feedbackId(row.getFeedbackId())
                .senderType(row.getSenderType())
                .senderId(row.getSenderId())
                .senderName(row.getSenderName())
                .content(row.getContent())
                .createTime(row.getCreateTime())
                .build();
    }
}
