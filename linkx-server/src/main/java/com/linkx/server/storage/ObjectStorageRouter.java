package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.storage.impl.CosObjectStorageBackend;
import com.linkx.server.storage.impl.MinioObjectStorageBackend;
import com.linkx.server.storage.impl.OssObjectStorageBackend;
import com.linkx.server.storage.impl.R2ObjectStorageBackend;
import com.linkx.server.storage.DirectMultipartCapableBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.linkx.server.service.FileStorageService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class ObjectStorageRouter {

    /** 非活跃后端探测超时：避免 MinIO/OSS 宕机时 exists/open 长时间阻塞 */
    private static final long PROBE_TIMEOUT_SECONDS = 4;

    private final LinkxProperties linkxProperties;
    private final MinioObjectStorageBackend minioBackend;
    private final OssObjectStorageBackend ossBackend;
    private final CosObjectStorageBackend cosBackend;
    private final R2ObjectStorageBackend r2Backend;

    public StorageProviderType activeProvider() {
        return StorageProviderType.fromWire(linkxProperties.getStorage().getProvider());
    }

    public ObjectStorageBackend activeBackend() {
        return backendFor(activeProvider());
    }

    public ObjectStorageBackend backendFor(StorageProviderType type) {
        switch (type) {
            case OSS:
                return ossBackend;
            case COS:
                return cosBackend;
            case R2:
                return r2Backend;
            case MINIO:
            default:
                return minioBackend;
        }
    }

    public void testConnection(StorageProviderType type) throws Exception {
        backendFor(type).testConnection();
    }

    public void reloadClients() {
        minioBackend.reloadClient();
        ossBackend.reloadClient();
        cosBackend.reloadClient();
        r2Backend.reloadClient();
    }

    /** 当前活跃后端是否支持浏览器直传分片（MinIO / R2） */
    public boolean supportsDirectMultipartUpload() {
        return activeBackend() instanceof DirectMultipartCapableBackend;
    }

    public DirectMultipartCapableBackend requireDirectMultipartBackend() {
        ObjectStorageBackend backend = activeBackend();
        if (backend instanceof DirectMultipartCapableBackend direct) {
            return direct;
        }
        throw new IllegalStateException("当前存储后端不支持直传分片: " + backend.providerType());
    }

    /** 该存储后端是否已配置凭证（未配置则跳过探测，避免无意义连接） */
    public boolean isProviderConfigured(StorageProviderType type) {
        switch (type) {
            case OSS: {
                LinkxProperties.Oss oss = linkxProperties.getOss();
                return StringUtils.hasText(oss.getAccessKeyId())
                        && StringUtils.hasText(oss.getAccessKeySecret())
                        && StringUtils.hasText(oss.getEndpoint())
                        && StringUtils.hasText(oss.getBucketName());
            }
            case COS: {
                LinkxProperties.Cos cos = linkxProperties.getCos();
                return StringUtils.hasText(cos.getSecretId())
                        && StringUtils.hasText(cos.getSecretKey())
                        && StringUtils.hasText(cos.getRegion())
                        && StringUtils.hasText(cos.getBucketName());
            }
            case R2: {
                LinkxProperties.R2 r2 = linkxProperties.getR2();
                return StringUtils.hasText(r2.getAccessKeyId())
                        && StringUtils.hasText(r2.getSecretAccessKey())
                        && StringUtils.hasText(r2.getEndpoint())
                        && StringUtils.hasText(r2.getBucketName());
            }
            case MINIO:
            default: {
                LinkxProperties.Minio minio = linkxProperties.getMinio();
                return StringUtils.hasText(minio.getAccessKey()) && StringUtils.hasText(minio.getSecretKey());
            }
        }
    }

    /**
     * 在已配置后端中定位对象（活跃后端优先）。
     * 上传走全局 active；历史文件留在原 MinIO/OSS/COS，读取时自动探测，无需迁移。
     */
    public StorageProviderType locateProviderForKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        for (StorageProviderType type : probeOrder(activeProvider())) {
            if (!isProviderConfigured(type)) {
                continue;
            }
            if (existsWithTimeout(backendFor(type), objectKey)) {
                return type;
            }
        }
        return null;
    }

    public ObjectStorageBackend backendForKey(String objectKey) {
        StorageProviderType located = locateProviderForKey(objectKey);
        return located != null ? backendFor(located) : activeBackend();
    }

    /**
     * 读取对象：先尝试全局活跃后端，再限时探测其它已配置后端（历史文件无需迁移）。
     */
    public FileStorageService.StoredObject openFromAnyBackend(String objectKey) throws Exception {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        StorageProviderType active = activeProvider();
        Exception lastFailure = null;
        if (isProviderConfigured(active)) {
            try {
                return backendFor(active).open(objectKey);
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        for (StorageProviderType type : probeOrder(active)) {
            if (type == active || !isProviderConfigured(type)) {
                continue;
            }
            try {
                return openWithTimeout(backendFor(type), objectKey);
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new java.io.FileNotFoundException("对象不存在: " + objectKey);
    }

    private boolean existsWithTimeout(ObjectStorageBackend backend, String objectKey) {
        try {
            return CompletableFuture.supplyAsync(() -> backend.exists(objectKey))
                    .orTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get();
        } catch (ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private FileStorageService.StoredObject openWithTimeout(ObjectStorageBackend backend, String objectKey)
            throws Exception {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return backend.open(objectKey);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).orTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new java.io.IOException("存储后端响应超时: " + backend.providerType());
            }
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private List<StorageProviderType> probeOrder(StorageProviderType preferred) {
        List<StorageProviderType> order = new ArrayList<>(EnumSet.allOf(StorageProviderType.class).size());
        order.add(preferred);
        for (StorageProviderType type : StorageProviderType.values()) {
            if (type != preferred) {
                order.add(type);
            }
        }
        return order;
    }

    /** CSP img-src / media-src / connect-src 允许的媒体源 */
    public List<String> mediaOriginsForCsp() {
        List<String> origins = new ArrayList<>();
        LinkxProperties.Minio minio = linkxProperties.getMinio();
        if (StringUtils.hasText(minio.getEndpoint())) {
            origins.add(minio.getEndpoint().trim());
            if (minio.getEndpoint().startsWith("http://")) {
                origins.add("https://" + minio.getEndpoint().substring(7));
            }
        }
        LinkxProperties.Oss oss = linkxProperties.getOss();
        if (StringUtils.hasText(oss.getEndpoint())) {
            String ep = oss.getEndpoint().trim();
            if (!ep.startsWith("http")) {
                origins.add("https://" + ep);
                origins.add("http://" + ep);
            } else {
                origins.add(ep);
            }
        }
        if (StringUtils.hasText(oss.getCnameDomain())) {
            String cname = oss.getCnameDomain().trim();
            origins.add("https://" + cname);
            origins.add("http://" + cname);
        }
        if (StringUtils.hasText(oss.getBucketName())) {
            String bucket = oss.getBucketName().trim();
            if (StringUtils.hasText(oss.getEndpoint())) {
                String ep = oss.getEndpoint().trim().replace("https://", "").replace("http://", "");
                origins.add("https://" + bucket + "." + ep);
            }
            if (StringUtils.hasText(oss.getCnameDomain())) {
                origins.add("https://" + bucket + "." + oss.getCnameDomain().trim());
            }
        }
        LinkxProperties.Cos cos = linkxProperties.getCos();
        if (StringUtils.hasText(cos.getRegion()) && StringUtils.hasText(cos.getBucketName())) {
            String region = cos.getRegion().trim();
            String bucket = cos.getBucketName().trim();
            origins.add("https://" + bucket + ".cos." + region + ".myqcloud.com");
            origins.add("http://" + bucket + ".cos." + region + ".myqcloud.com");
            origins.add("https://cos." + region + ".myqcloud.com");
            origins.add("http://cos." + region + ".myqcloud.com");
        }
        if (StringUtils.hasText(cos.getCnameDomain())) {
            String cname = cos.getCnameDomain().trim();
            origins.add("https://" + cname);
            origins.add("http://" + cname);
        }
        LinkxProperties.R2 r2 = linkxProperties.getR2();
        if (StringUtils.hasText(r2.getEndpoint())) {
            String ep = r2.getEndpoint().trim().replace("https://", "").replace("http://", "");
            origins.add("https://" + ep);
            origins.add("http://" + ep);
        }
        if (StringUtils.hasText(r2.getBucketName()) && StringUtils.hasText(r2.getEndpoint())) {
            String ep = r2.getEndpoint().trim().replace("https://", "").replace("http://", "");
            origins.add("https://" + ep + "/" + r2.getBucketName().trim());
            origins.add("http://" + ep + "/" + r2.getBucketName().trim());
        }
        if (StringUtils.hasText(r2.getCnameDomain())) {
            String cname = r2.getCnameDomain().trim();
            origins.add("https://" + cname);
            origins.add("http://" + cname);
        }
        return origins;
    }
}
