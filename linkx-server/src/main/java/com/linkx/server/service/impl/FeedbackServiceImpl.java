package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.FeedbackVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.service.FeedbackService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    public Feedback create(Long userId, String username, String type, String content, String contact) {
        Feedback feedback = Feedback.builder()
                .userId(userId)
                .username(username)
                .type(type)
                .content(content)
                .contact(contact)
                .status("pending")
                .build();
        feedbackMapper.insert(feedback);
        return feedback;
    }

    @Override
    public List<Feedback> listByUser(Long userId) {
        return feedbackMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Feedback::getUserId).eq(userId)
                        .orderBy(Feedback::getCreateTime, false)
        );
    }
}
