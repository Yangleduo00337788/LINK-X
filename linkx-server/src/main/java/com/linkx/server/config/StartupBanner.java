package com.linkx.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 应用启动后打印访问链接到控制台
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StartupBanner implements ApplicationRunner {

    private final LinkxProperties linkxProperties;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${server.port:8080}")
    private int port;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    @Value("${management.endpoints.web.exposure.include:health,info,metrics,prometheus}")
    private String actuatorEndpoints;

    @Override
    public void run(ApplicationArguments args) throws UnknownHostException {
        printBanner();
    }

    private void printBanner() throws UnknownHostException {
        String host = InetAddress.getLocalHost().getHostAddress();
        String baseUrl = String.format("http://localhost:%d%s", port, contextPath);
        String externalUrl = String.format("http://%s:%d%s", host, port, contextPath);

        // ANSI 颜色代码
        String cyan = "\033[96m";
        String green = "\033[92m";
        String yellow = "\033[93m";
        String reset = "\033[0m";
        String bold = "\033[1m";

        // 分隔线
        String divider = "════════════════════════════════════════════════════════════════";

        log.info("");
        log.info(cyan + divider + reset);
        log.info(cyan + "  " + bold + "🔗 LinkX Server 启动成功！" + reset);
        log.info(cyan + divider + reset);
        log.info("");

        // 基本信息
        log.info("  " + bold + "📍 服务地址" + reset);
        log.info("    本地访问:  " + green + baseUrl + reset);
        log.info("    网络访问:  " + yellow + externalUrl + reset);
        log.info("");

        // API 文档
        if (swaggerEnabled) {
            log.info("  " + bold + "📚 API 文档" + reset);
            log.info("    Swagger UI:   " + green + baseUrl + "/swagger-ui.html" + reset);
            log.info("    API Docs:    " + green + baseUrl + "/v3/api-docs" + reset);
            log.info("");
        }

        // 健康检查
        String endpoints = actuatorEndpoints.replaceAll("\\s+", "").toLowerCase();
        if (endpoints.contains("health")) {
            log.info("  " + bold + "🏥 健康检查" + reset);
            log.info("    健康状态:    " + green + "http://localhost:" + port + contextPath + "/actuator/health" + reset);
            log.info("    Liveness:   " + green + "http://localhost:" + port + contextPath + "/actuator/health/liveness" + reset);
            log.info("    Readiness:   " + green + "http://localhost:" + port + contextPath + "/actuator/health/readiness" + reset);
            log.info("");
        }

        // 监控指标
        if (endpoints.contains("prometheus")) {
            log.info("  " + bold + "📊 监控指标" + reset);
            log.info("    Prometheus:  " + green + "http://localhost:" + port + contextPath + "/actuator/prometheus" + reset);
            log.info("    Metrics:     " + green + "http://localhost:" + port + contextPath + "/actuator/metrics" + reset);
            log.info("");
        }

        // 认证相关
        log.info("  " + bold + "🔐 认证接口" + reset);
        log.info("    登录:       POST " + green + baseUrl + "/auth/login" + reset);
        log.info("    注册:       POST " + green + baseUrl + "/auth/register" + reset);
        log.info("    验证码:     GET  " + green + baseUrl + "/auth/captcha" + reset);
        log.info("    刷新Token:  POST " + green + baseUrl + "/auth/refresh" + reset);
        log.info("    登出:       POST " + green + baseUrl + "/auth/logout" + reset);
        log.info("");

        // WebSocket - 端口从 linkx.im.websocket-port 配置读取，默认 8081
        int wsPort = linkxProperties.getIm().getWebsocketPort();
        log.info("  " + bold + "🔌 即时通讯" + reset);
        log.info("    WebSocket:  ws://localhost:" + wsPort + "/ws");
        log.info("");

        // 提示信息
        log.info(cyan + "  💡 提示: 使用 Swagger UI 可以在线调试所有 API" + reset);
        log.info(cyan + divider + reset);
        log.info("");
    }
}
