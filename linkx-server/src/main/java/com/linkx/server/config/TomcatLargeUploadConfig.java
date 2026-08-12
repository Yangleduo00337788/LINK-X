package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 显式放大 Tomcat Connector 的 POST 体上限（与 yml 互补，避免仅改 multipart 仍被 2MB 默认截断）。
 */
@Slf4j
@Configuration
public class TomcatLargeUploadConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatLargeUploadCustomizer(
            @Value("${server.tomcat.max-http-form-post-size:310MB}") DataSize maxHttpFormPostSize) {
        return factory -> factory.addConnectorCustomizers(connector -> applyUploadLimits(connector, maxHttpFormPostSize));
    }

    private static void applyUploadLimits(Connector connector, DataSize maxHttpFormPostSize) {
        long bytes = maxHttpFormPostSize.toBytes();
        int maxPostSize;
        if (bytes < 0) {
            maxPostSize = -1;
        } else if (bytes > Integer.MAX_VALUE) {
            maxPostSize = -1;
        } else {
            maxPostSize = (int) bytes;
        }
        connector.setMaxPostSize(maxPostSize);
        connector.setProperty("connectionUploadTimeout", "1800000");
        connector.setProperty("disableUploadTimeout", "false");
        log.info("Tomcat connector upload limits: maxPostSize={} bytes, connectionUploadTimeout=1800000ms",
                maxPostSize < 0 ? "unlimited" : maxPostSize);
    }
}
