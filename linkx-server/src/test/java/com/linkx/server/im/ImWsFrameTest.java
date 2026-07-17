package com.linkx.server.im;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImWsFrame WebSocket帧测试
 */
@DisplayName("ImWsFrame WebSocket帧测试")
class ImWsFrameTest {

    @Nested
    @DisplayName("字段设置测试")
    class FieldTests {

        @Test
        @DisplayName("使用setter设置字段应成功")
        void settersWork() {
            ImWsFrame frame = new ImWsFrame();

            frame.setAction("send_message");
            frame.setClientMsgId("msg-123");
            frame.setConversationId("conv-456");
            frame.setMsgType("text");
            frame.setContent("Hello World");
            frame.setCode(200);
            frame.setMessage("success");
            frame.setFileName("test.txt");
            frame.setFileSize(1024L);
            frame.setFileUrl("http://example.com/file.txt");

            assertEquals("send_message", frame.getAction());
            assertEquals("msg-123", frame.getClientMsgId());
            assertEquals("conv-456", frame.getConversationId());
            assertEquals("text", frame.getMsgType());
            assertEquals("Hello World", frame.getContent());
            assertEquals(200, frame.getCode());
            assertEquals("success", frame.getMessage());
            assertEquals("test.txt", frame.getFileName());
            assertEquals(1024L, frame.getFileSize());
            assertEquals("http://example.com/file.txt", frame.getFileUrl());
        }

        @Test
        @DisplayName("data字段应可存储任意对象")
        void dataField_acceptsAnyObject() {
            ImWsFrame frame = new ImWsFrame();

            frame.setData("string data");
            assertEquals("string data", frame.getData());

            frame.setData(123);
            assertEquals(123, frame.getData());

            String[] arr = new String[]{"a", "b"};
            frame.setData(arr);
            assertArrayEquals(arr, (String[]) frame.getData());
        }
    }

    @Nested
    @DisplayName("消息类型测试")
    class MsgTypeTests {

        @Test
        @DisplayName("文本消息应正确设置")
        void textMessage() {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("send_message");
            frame.setMsgType("text");
            frame.setContent("这是一条文本消息");

            assertEquals("text", frame.getMsgType());
            assertEquals("这是一条文本消息", frame.getContent());
        }

        @Test
        @DisplayName("图片消息应正确设置")
        void imageMessage() {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("send_message");
            frame.setMsgType("image");
            frame.setFileUrl("http://example.com/image.jpg");

            assertEquals("image", frame.getMsgType());
            assertEquals("http://example.com/image.jpg", frame.getFileUrl());
        }

        @Test
        @DisplayName("文件消息应正确设置")
        void fileMessage() {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("send_message");
            frame.setMsgType("file");
            frame.setFileName("document.pdf");
            frame.setFileSize(1024 * 1024L);
            frame.setFileUrl("http://example.com/doc.pdf");

            assertEquals("file", frame.getMsgType());
            assertEquals("document.pdf", frame.getFileName());
            assertEquals(1024 * 1024L, frame.getFileSize());
        }
    }

    @Nested
    @DisplayName("响应帧测试")
    class ResponseFrameTests {

        @Test
        @DisplayName("成功响应应正确设置")
        void successResponse() {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("send_message");
            frame.setCode(200);
            frame.setMessage("success");
            frame.setClientMsgId("msg-001");

            assertEquals(200, frame.getCode());
            assertEquals("success", frame.getMessage());
            assertEquals("msg-001", frame.getClientMsgId());
        }

        @Test
        @DisplayName("错误响应应正确设置")
        void errorResponse() {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("send_message");
            frame.setCode(400);
            frame.setMessage("参数错误");
            frame.setClientMsgId("msg-002");

            assertEquals(400, frame.getCode());
            assertEquals("参数错误", frame.getMessage());
        }
    }
}
