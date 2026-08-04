package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.vo.AdminReviewRiskContextVO;
import com.linkx.server.entity.admin.SysReviewTask;

public interface ReviewRiskScoringService {

    AdminReviewRiskContextVO buildContext(SysReviewTask task);

    String elevateLevel(String baseLevel, Long subjectUserId);

    int computeUserHistoryScore(Long subjectUserId);

    String scoreToLevel(int score);
}
