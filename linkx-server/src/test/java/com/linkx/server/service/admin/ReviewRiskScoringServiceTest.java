package com.linkx.server.service.admin;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.vo.AdminReviewRiskContextVO;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.admin.impl.ReviewRiskScoringServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewRiskScoringService 风险评分")
class ReviewRiskScoringServiceTest {

    @Mock SysRiskEventMapper riskEventMapper;

    private ReviewRiskScoringService service;

    @BeforeEach
    void setUp() {
        LinkxProperties props = new LinkxProperties();
        service = new ReviewRiskScoringServiceImpl(riskEventMapper, props);
    }

    @Test
    void scoreToLevel_usesThresholds() {
        assertEquals("low", service.scoreToLevel(10));
        assertEquals("medium", service.scoreToLevel(45));
        assertEquals("high", service.scoreToLevel(70));
        assertEquals("critical", service.scoreToLevel(90));
    }

    @Test
    void buildContext_pendingHighTask() {
        when(riskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(riskEventMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        SysReviewTask task = SysReviewTask.builder()
                .riskLevel("high")
                .targetType(SysReviewTask.TARGET_USER)
                .targetId("1001")
                .build();
        AdminReviewRiskContextVO ctx = service.buildContext(task);
        assertEquals("high", ctx.getComputedRiskLevel());
        assertEquals(70, ctx.getRiskScore());
    }
}
