package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.ShortVideoTranscodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShortVideoTranscodeTask {

    private final ShortVideoTranscodeService shortVideoTranscodeService;

    public int processPending() {
        return shortVideoTranscodeService.processPendingBatch();
    }
}
