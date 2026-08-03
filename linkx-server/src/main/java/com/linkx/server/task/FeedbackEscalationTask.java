package com.linkx.server.task;

import com.linkx.server.service.admin.FeedbackEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackEscalationTask {

    private final FeedbackEscalationService feedbackEscalationService;

    public void scanOverdueFeedback() {
        feedbackEscalationService.processOverdueEscalations();
    }
}
