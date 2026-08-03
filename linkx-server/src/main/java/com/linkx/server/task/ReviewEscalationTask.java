package com.linkx.server.task;

import com.linkx.server.service.admin.ReviewEscalationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewEscalationTask {

    private final ReviewEscalationService reviewEscalationService;

    public void scanOverdueReviews() {
        reviewEscalationService.processOverdueEscalations();
    }
}
