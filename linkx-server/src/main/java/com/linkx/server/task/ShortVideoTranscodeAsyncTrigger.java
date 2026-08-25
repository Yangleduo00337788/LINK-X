package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.ShortVideoTranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 上传/重新转码后立即异步执行，无需等待定时任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortVideoTranscodeAsyncTrigger {

    private final ShortVideoTranscodeService shortVideoTranscodeService;

    @Async("shortVideoTranscodeExecutor")
    public void trigger(Long postId) {
        if (postId == null) {
            return;
        }
        try {
            shortVideoTranscodeService.transcodePostIfPending(postId);
        } catch (Exception ex) {
            log.warn("短视频异步转码触发失败 postId={}: {}", postId, ex.getMessage());
        }
    }
}
