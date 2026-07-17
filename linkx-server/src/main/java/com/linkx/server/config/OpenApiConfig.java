package com.linkx.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 (SpringDoc) 配置
 * 访问地址: /swagger-ui.html
 * API JSON: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI linkxOpenAPI() {
        String jwtSchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("LinkX IM API")
                        .description("企业级即时通讯与协同平台 RESTful API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LinkX Team")
                                .email("support@linkx.example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url(contextPath)
                                .description("API Base URL"),
                        new Server()
                                .url("http://localhost:8080/api")
                                .description("本地开发环境")))
                .addSecurityItem(new SecurityRequirement().addList(jwtSchemeName))
                .components(new Components()
                        .addSecuritySchemes(jwtSchemeName,
                                new SecurityScheme()
                                        .name(jwtSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请在登录后获取 Access Token，并在请求头中携带：Authorization: Bearer <token>")));
    }
}
