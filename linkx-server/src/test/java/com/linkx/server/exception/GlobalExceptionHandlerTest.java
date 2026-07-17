package com.linkx.server.exception;

import com.linkx.server.common.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局异常处理器测试
 */
@DisplayName("GlobalExceptionHandler 全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("handleCustomException 处理自定义异常测试")
    class HandleCustomExceptionTests {

        @Test
        @DisplayName("400错误应返回BAD_REQUEST")
        void customException_400_returnsBadRequest() {
            CustomException ex = new CustomException(400, "参数错误");

            ResponseEntity<Result<?>> response = handler.handleCustomException(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(400, response.getBody().getCode());
            assertEquals("参数错误", response.getBody().getMessage());
        }

        @Test
        @DisplayName("401错误应返回UNAUTHORIZED")
        void customException_401_returnsUnauthorized() {
            CustomException ex = new CustomException(401, "未登录");

            ResponseEntity<Result<?>> response = handler.handleCustomException(ex);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals(401, response.getBody().getCode());
        }

        @Test
        @DisplayName("403错误应返回FORBIDDEN")
        void customException_403_returnsForbidden() {
            CustomException ex = new CustomException(403, "无权限");

            ResponseEntity<Result<?>> response = handler.handleCustomException(ex);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals(403, response.getBody().getCode());
        }

        @Test
        @DisplayName("429错误应返回TOO_MANY_REQUESTS")
        void customException_429_returnsTooManyRequests() {
            CustomException ex = new CustomException(429, "请求过于频繁");

            ResponseEntity<Result<?>> response = handler.handleCustomException(ex);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertEquals(429, response.getBody().getCode());
        }

        @Test
        @DisplayName("500错误应返回INTERNAL_SERVER_ERROR")
        void customException_500_returnsInternalServerError() {
            CustomException ex = new CustomException(500, "服务器错误");

            ResponseEntity<Result<?>> response = handler.handleCustomException(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, response.getBody().getCode());
        }

        @Test
        @DisplayName("仅传消息的异常默认code为500")
        void customException_onlyMessage_defaultsTo500() {
            CustomException ex = new CustomException("仅消息的异常");

            ResponseEntity<Result<?>> response = handler.handleCustomException(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, response.getBody().getCode());
            assertEquals("仅消息的异常", response.getBody().getMessage());
        }
    }

    @Nested
    @DisplayName("handleException 处理通用异常测试")
    class HandleExceptionTests {

        @Test
        @DisplayName("通用异常应返回500")
        void generalException_returns500() {
            Exception ex = new RuntimeException("未知错误");

            ResponseEntity<Result<?>> response = handler.handleException(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, response.getBody().getCode());
            assertEquals("系统内部繁忙，请稍后再试", response.getBody().getMessage());
        }

        @Test
        @DisplayName("空消息异常应返回默认消息")
        void emptyMessageException_returnsDefaultMessage() {
            Exception ex = new RuntimeException();

            ResponseEntity<Result<?>> response = handler.handleException(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, response.getBody().getCode());
        }
    }

    @Nested
    @DisplayName("CustomException 构造器测试")
    class CustomExceptionTests {

        @Test
        @DisplayName("仅消息构造器应设置默认code=500")
        void messageOnlyConstructor_setsDefaultCode() {
            CustomException ex = new CustomException("测试消息");

            assertEquals(500, ex.getCode());
            assertEquals("测试消息", ex.getMessage());
        }

        @Test
        @DisplayName("code和message构造器应正确设置")
        void codeAndMessageConstructor_setsCorrectValues() {
            CustomException ex = new CustomException(400, "自定义错误");

            assertEquals(400, ex.getCode());
            assertEquals("自定义错误", ex.getMessage());
        }

        @Test
        @DisplayName("异常应可被抛出和捕获")
        void exception_canBeThrownAndCaught() {
            CustomException original = new CustomException(401, "需要登录");

            CustomException caught = assertThrows(CustomException.class, () -> {
                throw original;
            });

            assertEquals(401, caught.getCode());
        }
    }
}
