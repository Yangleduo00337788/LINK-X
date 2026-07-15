package com.linkx.server.service;

import com.linkx.server.entity.Feedback;

import java.util.List;

public interface FeedbackService {

    Feedback create(Long userId, String username, String type, String content, String contact);

    List<Feedback> listByUser(Long userId);
}
