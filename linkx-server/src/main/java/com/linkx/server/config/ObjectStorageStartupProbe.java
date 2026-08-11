package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.storage.StorageProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时探测当前对象存储后端（失败仅告警，不阻断启动；管理端可热切换修复）。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class ObjectStorageStartupProbe implements ApplicationRunner {

    private final ObjectStorageRouter objectStorageRouter;

    @Override
    public void run(ApplicationArguments args) {
        StorageProviderType provider = objectStorageRouter.activeProvider();
        try {
            objectStorageRouter.testConnection(provider);
            log.info("对象存储 [{}] 连通性检查通过", provider.toWire());
        } catch (Exception e) {
            log.warn("对象存储 [{}] 连通性检查失败（可在管理端配置后热切换）: {}",
                    provider.toWire(), e.getMessage());
        }
    }
}
