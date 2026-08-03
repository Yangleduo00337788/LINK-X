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

    /**
     * 按分流规则改派（已有处理人且匹配到新处理人时更新）。
     *
     * @return 是否发生改派
     */
    boolean tryReassign(Feedback feedback);
}
