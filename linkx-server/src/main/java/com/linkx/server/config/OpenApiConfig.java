package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

/**
 * OpenAPI 3 (SpringDoc) 配置
 * 访问地址: /swagger-ui.html?lang=zh_CN 或 ?lang=en
 * API JSON: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String JWT_SCHEME = "bearerAuth";

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${linkx.openapi.server-url:http://localhost:8080/api}")
    private String openapiServerUrl;

    @Bean
    public OpenAPI linkxOpenAPI() {
        // 文案由 OpenApiCustomizer 按当前 Locale 填充，避免单例 Bean 钉死一种语言
        return new OpenAPI()
                .info(new Info()
                        .title("LinkX IM API")
                        .version("1.0.0")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME,
                                new SecurityScheme()
                                        .name(JWT_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer linkxOpenApiI18nCustomizer(MessageSource messageSource) {
        return openApi -> {
            Locale locale = LocaleContextHolder.getLocale();
            String title = msg(messageSource, "openapi.info.title", locale);
            String description = msg(messageSource, "openapi.info.description", locale);
            String contact = msg(messageSource, "openapi.info.contact", locale);
            String serverRelative = msg(messageSource, "openapi.server.relative", locale);
            String serverLocal = msg(messageSource, "openapi.server.local", locale);
            String bearerDesc = msg(messageSource, "openapi.security.bearer", locale);

            openApi.setInfo(new Info()
                    .title(title)
                    .description(description)
                    .version("1.0.0")
                    .contact(new Contact().name(contact).email("support@linkx.example.com"))
                    .license(new License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")));

            openApi.setServers(List.of(
                    new Server().url(contextPath).description(serverRelative),
                    new Server().url(openapiServerUrl).description(serverLocal)
            ));

            if (openApi.getComponents() != null
                    && openApi.getComponents().getSecuritySchemes() != null
                    && openApi.getComponents().getSecuritySchemes().get(JWT_SCHEME) != null) {
                openApi.getComponents().getSecuritySchemes().get(JWT_SCHEME).setDescription(bearerDesc);
            }
        };
    }

    private static String msg(MessageSource source, String code, Locale locale) {
        return source.getMessage(code, null, code, locale);
    }
}
