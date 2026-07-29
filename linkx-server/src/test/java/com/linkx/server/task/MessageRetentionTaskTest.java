package com.linkx.server.task;

import com.linkx.server.service.FileStorageService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageRetentionTask 测试
 */
@DisplayName("MessageRetentionTask 消息留存清理测试")
class MessageRetentionTaskTest extends BaseIntegrationTest {

    @Autowired
    private MessageRetentionTask messageRetentionTask;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("任务应可正常执行不抛异常")
    void purgeExpiredMessages_shouldNotThrow() {
        assertDoesNotThrow(() -> messageRetentionTask.purgeExpiredMessages());
    }

    @Test
    @DisplayName("FileStorageService 应支持异步删除方法")
    void fileStorageService_shouldHaveAsyncDelete() {
        FileStorageService service = applicationContext.getBean(FileStorageService.class);
        assertNotNull(service);
        // 验证异步删除方法存在且可调用（null值安全处理）
        assertDoesNotThrow(() -> service.deleteFileAsync(null));
        assertDoesNotThrow(() -> service.deleteFileAsync(""));
        assertDoesNotThrow(() -> service.deleteFileAsync("  "));
    }
}
