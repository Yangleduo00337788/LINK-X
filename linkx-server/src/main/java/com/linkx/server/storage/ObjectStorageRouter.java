package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.storage.impl.LocalObjectStorageBackend;
import com.linkx.server.storage.impl.MinioObjectStorageBackend;
import com.linkx.server.storage.impl.OssObjectStorageBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObjectStorageRouter {

    private final LinkxProperties linkxProperties;
    private final MinioObjectStorageBackend minioBackend;
    private final OssObjectStorageBackend ossBackend;
    private final LocalObjectStorageBackend localBackend;

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
            case LOCAL:
                return localBackend;
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
        return origins;
    }
}
