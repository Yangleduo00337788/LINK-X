package com.linkx.server.service.admin;

import com.linkx.server.entity.Feedback;

import java.util.Optional;

public interface FeedbackDispatchService {

    /**
     * 按启用规则（优先级降序）匹配首个处理人。
     */
    Optional<Long> matchAssignee(Feedback feedback);

    /**
     * 新建反馈后自动分流（仅当尚未指派时）。
     */
    void applyAutoDispatch(Feedback feedback);
}
