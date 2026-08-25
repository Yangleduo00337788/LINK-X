package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.ShortVideoPost;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ShortVideoPostMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.ShortVideoTranscodeService;
import com.linkx.server.storage.ObjectStorageBackend;
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.storage.StorageProviderType;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortVideoTranscodeServiceImpl implements ShortVideoTranscodeService {

    private final ShortVideoPostMapper postMapper;
    private final FileStorageService fileStorageService;
    private final ObjectStorageRouter objectStorageRouter;
    private final LinkxProperties linkxProperties;

    @Override
    public int processPendingBatch() {
        LinkxProperties.ShortVideo config = linkxProperties.getShortVideo();
        if (config == null || !config.isTranscodeEnabled()) {
            return 0;
        }
        int batchSize = config.getTranscodeBatchSize();
        List<ShortVideoPost> pending = postMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("deleted", 0)
                        .eq("transcode_status", "pending")
                        .orderBy("create_time", true)
                        .limit(batchSize));
        int success = 0;
        for (ShortVideoPost post : pending) {
            if (transcodeOne(post, config)) {
                success++;
            }
        }
        return success;
    }

    @Override
    public void enqueueRetranscode(Long postId) {
        LinkxProperties.ShortVideo config = linkxProperties.getShortVideo();
        if (config == null || !config.isTranscodeEnabled()) {
            throw new CustomException(400, "短视频转码未启用");
        }
        ShortVideoPost post = postMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0));
        if (post == null) {
            throw new CustomException(404, "作品不存在");
        }
        String status = post.getTranscodeStatus();
        if ("pending".equals(status)) {
            throw new CustomException(409, "作品已在转码队列中");
        }
        if ("processing".equals(status)) {
            throw new CustomException(409, "作品正在转码中");
        }
        UpdateChain.of(ShortVideoPost.class)
                .set(ShortVideoPost::getTranscodeStatus, "pending")
                .set(ShortVideoPost::getTranscodedVideoKey, null)
                .where(ShortVideoPost::getId).eq(postId)
                .update();
    }

    private boolean transcodeOne(ShortVideoPost post, LinkxProperties.ShortVideo config) {
        Long postId = post.getId();
        markStatus(postId, "processing");
        Path source = null;
        Path output = null;
        try {
            source = Files.createTempFile("sv-src-", ".mp4");
            output = Files.createTempFile("sv-out-", ".mp4");
            try (FileStorageService.StoredObject stored = fileStorageService.openObjectOnProvider(
                    post.getVideoKey(), post.getStorageProvider());
                 InputStream in = stored.stream()) {
                Files.copy(in, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            runFfmpeg(config, source, output);
            String outputKey = fileStorageService.allocateObjectName("transcoded.mp4");
            ObjectStorageBackend backend = objectStorageRouter.backendFor(
                    StorageProviderType.fromWire(post.getStorageProvider()));
            long size = Files.size(output);
            try (InputStream out = Files.newInputStream(output)) {
                backend.putObject(outputKey, out, size, "video/mp4");
            }
            UpdateChain.of(ShortVideoPost.class)
                    .set(ShortVideoPost::getTranscodedVideoKey, outputKey)
                    .set(ShortVideoPost::getTranscodeStatus, "completed")
                    .where(ShortVideoPost::getId).eq(postId)
                    .update();
            return true;
        } catch (Exception e) {
            log.warn("短视频转码失败 postId={}: {}", postId, e.getMessage(), e);
            markStatus(postId, "failed");
            return false;
        } finally {
            deleteQuietly(source);
            deleteQuietly(output);
        }
    }

    private void runFfmpeg(LinkxProperties.ShortVideo config, Path source, Path output) throws Exception {
        String ffmpeg = StringUtils.hasText(config.getFfmpegPath()) ? config.getFfmpegPath() : "ffmpeg";
        int height = config.getTranscodeHeight();
        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg,
                "-y",
                "-i", source.toString(),
                "-vf", "scale=-2:" + height,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-c:a", "aac",
                "-movflags", "+faststart",
                output.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String ffmpegLog = readProcessOutput(process.getInputStream());
        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg 超时");
        }
        if (process.exitValue() != 0) {
            String tail = tailLines(ffmpegLog, 8);
            throw new IllegalStateException("ffmpeg 退出码 " + process.exitValue()
                    + (tail.isBlank() ? "" : "：" + tail));
        }
        if (!Files.exists(output) || Files.size(output) <= 0) {
            throw new IllegalStateException("转码输出为空");
        }
    }

    private void markStatus(Long postId, String status) {
        UpdateChain.of(ShortVideoPost.class)
                .set(ShortVideoPost::getTranscodeStatus, status)
                .where(ShortVideoPost::getId).eq(postId)
                .update();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            /* best effort */
        }
    }

    private static String readProcessOutput(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String tailLines(String text, int maxLines) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] lines = text.strip().split("\\R");
        int from = Math.max(0, lines.length - maxLines);
        return String.join(" | ", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }
}
