package com.linkx.server.config;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * SpringDoc 自定义：通用错误响应随 Locale 切换文案。
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OperationCustomizer operationCustomizer(MessageSource messageSource) {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            Locale locale = LocaleContextHolder.getLocale();
            addResponse(responses, "401", messageSource, "openapi.response.401", "openapi.response.401.example", locale);
            addResponse(responses, "403", messageSource, "openapi.response.403", "openapi.response.403.example", locale);
            addResponse(responses, "400", messageSource, "openapi.response.400", "openapi.response.400.example", locale);
            addResponse(responses, "500", messageSource, "openapi.response.500", "openapi.response.500.example", locale);

            return operation;
        };
    }

    private static void addResponse(ApiResponses responses, String code, MessageSource messageSource,
                                    String descKey, String exampleKey, Locale locale) {
        String desc = messageSource.getMessage(descKey, null, descKey, locale);
        String example = messageSource.getMessage(exampleKey, null, "", locale);
        responses.addApiResponse(code, new ApiResponse()
                .description(desc)
                .content(new Content().addMediaType("application/json",
                        new MediaType().example(example))));
    }
}
