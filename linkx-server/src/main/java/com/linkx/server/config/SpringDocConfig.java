package com.linkx.server.config;

import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc 自定义配置
 */
@Configuration
public class SpringDocConfig {

    /**
     * 自定义操作处理器 - 添加通用错误响应
     */
    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            responses.addApiResponse("401", new ApiResponse()
                    .description("未授权 - 需要登录或 Token 已过期")
                    .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                            new io.swagger.v3.oas.models.media.MediaType().example("{\"code\":401,\"message\":\"未登录或登录已过期\"}"))));

            responses.addApiResponse("403", new ApiResponse()
                    .description("禁止访问 - 无权限")
                    .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                            new io.swagger.v3.oas.models.media.MediaType().example("{\"code\":403,\"message\":\"无权限访问\"}"))));

            responses.addApiResponse("400", new ApiResponse()
                    .description("请求错误 - 参数校验失败")
                    .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                            new io.swagger.v3.oas.models.media.MediaType().example("{\"code\":400,\"message\":\"请求参数错误\"}"))));

            responses.addApiResponse("500", new ApiResponse()
                    .description("服务器内部错误")
                    .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                            new io.swagger.v3.oas.models.media.MediaType().example("{\"code\":500,\"message\":\"服务器内部错误\"}"))));

            return operation;
        };
    }
}
