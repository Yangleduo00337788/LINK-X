package com.linkx.server.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应封装测试
 */
@DisplayName("Result 统一响应封装测试")
class ResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("success 静态工厂方法测试")
    class SuccessTests {

        @Test
        @DisplayName("success()应返回code=200, message=success, data=null")
        void success_noData() {
            Result<String> result = Result.success();

            assertEquals(200, result.getCode());
            assertEquals("success", result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("success(T data)应正确设置data字段")
        void success_withData() {
            String data = "test data";
            Result<String> result = Result.success(data);

            assertEquals(200, result.getCode());
            assertEquals("success", result.getMessage());
            assertEquals("test data", result.getData());
        }

        @Test
        @DisplayName("success支持任意泛型类型")
        void success_withObjectData() {
            Result<Object> result = Result.success(new Object());
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("success支持null作为data")
        void success_withNullData() {
            Result<String> result = Result.success(null);
            assertNull(result.getData());
        }

        @Test
        @DisplayName("success支持复杂对象")
        void success_withComplexObject() {
            Map<String, Object> complexData = Map.of("name", "test", "count", 42);
            Result<Map<String, Object>> result = Result.success(complexData);

            assertEquals(200, result.getCode());
            assertEquals("test", result.getData().get("name"));
            assertEquals(42, result.getData().get("count"));
        }
    }

    @Nested
    @DisplayName("error 静态工厂方法测试")
    class ErrorTests {

        @Test
        @DisplayName("error(String message)应返回code=500")
        void error_withMessage() {
            Result<String> result = Result.error("Something went wrong");

            assertEquals(500, result.getCode());
            assertEquals("Something went wrong", result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("error(Integer code, String message)应正确设置所有字段")
        void error_withCodeAndMessage() {
            Result<String> result = Result.error(400, "Bad Request");

            assertEquals(400, result.getCode());
            assertEquals("Bad Request", result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("error支持常见错误码")
        void error_commonCodes() {
            assertEquals(400, Result.error(400, "Bad Request").getCode());
            assertEquals(401, Result.error(401, "Unauthorized").getCode());
            assertEquals(403, Result.error(403, "Forbidden").getCode());
            assertEquals(404, Result.error(404, "Not Found").getCode());
            assertEquals(500, Result.error(500, "Server Error").getCode());
        }

        @Test
        @DisplayName("error的data字段始终为null")
        void error_dataAlwaysNull() {
            Result<Object> result = Result.error(400, "error");
            assertNull(result.getData());
        }
    }

    @Nested
    @DisplayName("JSON序列化测试")
    class JsonSerializationTest {

        @Test
        @DisplayName("成功响应应正确序列化为JSON")
        void successJsonSerialization() throws Exception {
            Result<String> result = Result.success("hello");
            String json = objectMapper.writeValueAsString(result);

            assertTrue(json.contains("\"code\":200"));
            assertTrue(json.contains("\"message\":\"success\""));
            assertTrue(json.contains("\"data\":\"hello\""));
        }

        @Test
        @DisplayName("错误响应应正确序列化为JSON")
        void errorJsonSerialization() throws Exception {
            Result<String> result = Result.error(400, "Invalid input");
            String json = objectMapper.writeValueAsString(result);

            assertTrue(json.contains("\"code\":400"));
            assertTrue(json.contains("\"message\":\"Invalid input\""));
            assertTrue(json.contains("\"data\":null"));
        }

        @Test
        @DisplayName("JSON应能被反序列化回Result对象")
        void jsonDeserialization() throws Exception {
            String json = "{\"code\":200,\"message\":\"success\",\"data\":\"test\"}";
            Result<String> result = objectMapper.readValue(json, Result.class);

            assertEquals(200, result.getCode());
            assertEquals("success", result.getMessage());
            assertEquals("test", result.getData());
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数应创建空Result")
        void noArgConstructor() {
            Result<String> result = new Result<>();

            assertNull(result.getCode());
            assertNull(result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("私有构造函数应通过静态方法调用")
        void privateConstructorViaStaticFactory() {
            Result<String> result = Result.success("data");

            assertNotNull(result);
            assertEquals("success", result.getMessage());
        }
    }

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCasesTest {

        @Test
        @DisplayName("超长错误消息")
        void longErrorMessage() {
            String longMsg = "x".repeat(10000);
            Result<String> result = Result.error(longMsg);
            assertEquals(longMsg, result.getMessage());
        }

        @Test
        @DisplayName("特殊字符消息")
        void specialCharsInMessage() {
            Result<String> result = Result.error("<script>alert('xss')</script>");
            assertTrue(result.getMessage().contains("<script>"));
        }

        @Test
        @DisplayName("Unicode消息")
        void unicodeMessage() {
            Result<String> result = Result.error("操作成功！");
            assertEquals("操作成功！", result.getMessage());
        }

        @Test
        @DisplayName("空消息")
        void emptyMessage() {
            Result<String> result = Result.error("");
            assertEquals("", result.getMessage());
        }

        @Test
        @DisplayName("0作为错误码")
        void zeroAsCode() {
            Result<String> result = Result.error(0, "Zero code");
            assertEquals(0, result.getCode());
        }

        @Test
        @DisplayName("负数作为错误码")
        void negativeCode() {
            Result<String> result = Result.error(-1, "Negative code");
            assertEquals(-1, result.getCode());
        }
    }
}
