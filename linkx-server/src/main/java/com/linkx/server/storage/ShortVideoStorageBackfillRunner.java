package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.ShortVideoPost;
import com.linkx.server.mapper.ShortVideoPostMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 为历史短视频回填 storage_provider，便于按全局存储配置隔离展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortVideoStorageBackfillRunner implements ApplicationRunner {

    private final ShortVideoPostMapper postMapper;
    private final ObjectStorageRouter objectStorageRouter;

    @Override
    public void run(ApplicationArguments args) {
        List<ShortVideoPost> rows = postMapper.selectListByQuery(
                QueryWrapper.create().isNull("storage_provider").eq("deleted", 0));
        if (rows.isEmpty()) {
            return;
        }
        int updated = 0;
        for (ShortVideoPost post : rows) {
            if (!StringUtils.hasText(post.getVideoKey())) {
                continue;
            }
            StorageProviderType located = objectStorageRouter.locateProviderForKey(post.getVideoKey());
            if (located == null) {
                continue;
            }
            post.setStorageProvider(located.toWire());
            postMapper.update(post);
            updated++;
        }
        if (updated > 0) {
            log.info("短视频 storage_provider 回填完成: {} 条", updated);
        }
    }
}
