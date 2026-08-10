package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.IpGeoService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpGeoServiceImpl implements IpGeoService {

    private static final String CLASSPATH_XDB = "ip2region/ip2region.xdb";

    private final LinkxProperties linkxProperties;

    private volatile Searcher searcher;

    @PostConstruct
    void init() {
        try {
            byte[] db = loadDbBytes();
            if (db == null || db.length == 0) {
                log.info("IP 归属地库未配置，公网 IP 将显示为「未知」（内网仍可识别）");
                return;
            }
            searcher = Searcher.newWithBuffer(db);
            log.info("IP 归属地库已加载，size={} bytes", db.length);
        } catch (Exception e) {
            log.warn("IP 归属地库加载失败，将降级为启发式解析: {}", e.getMessage());
            searcher = null;
        }
    }

    @PreDestroy
    void destroy() {
        Searcher s = searcher;
        searcher = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    @Override
    public String resolve(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String normalized = ip.trim();
        if (isPrivateOrLocal(normalized)) {
            return "内网";
        }
        Searcher s = searcher;
        if (s == null) {
            return "未知";
        }
        try {
            String raw = s.search(normalized);
            return formatRegion(raw);
        } catch (Exception e) {
            log.debug("IP 归属地查询失败 ip={}: {}", normalized, e.getMessage());
            return "未知";
        }
    }

    private byte[] loadDbBytes() throws Exception {
        String configured = linkxProperties.getIpGeo() != null
                ? linkxProperties.getIpGeo().getXdbPath() : null;
        if (StringUtils.hasText(configured)) {
            Path path = Path.of(configured.trim());
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
            log.warn("配置的 IP 归属地库不存在: {}", path.toAbsolutePath());
        }
        ClassPathResource resource = new ClassPathResource(CLASSPATH_XDB);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    static boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isAnyLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /** ip2region 原始格式：国家|区域|省份|城市|ISP，0 表示空 */
    static String formatRegion(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未知";
        }
        String formatted = Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(part -> !part.isEmpty() && !"0".equals(part))
                .collect(Collectors.joining(" "));
        return StringUtils.hasText(formatted) ? formatted : "未知";
    }
}
