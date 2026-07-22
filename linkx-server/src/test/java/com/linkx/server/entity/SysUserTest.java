package com.linkx.server.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SysUser 实体测试
 */
@DisplayName("SysUser 实体测试")
class SysUserTest {

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("使用Builder创建对象应成功")
        void builderCreatesObject() {
            SysUser user = SysUser.builder()
                    .id(1L)
                    .username("testuser")
                    .nickname("测试用户")
                    .password("hashedPassword")
                    .status(1)
                    .deleted(0)
                    .build();

            assertNotNull(user);
            assertEquals(1L, user.getId());
            assertEquals("testuser", user.getUsername());
            assertEquals("测试用户", user.getNickname());
            assertEquals("hashedPassword", user.getPassword());
            assertEquals(1, user.getStatus());
            assertEquals(0, user.getDeleted());
        }

        @Test
        @DisplayName("Builder链式调用应正常工作")
        void builderChainWorks() {
            SysUser user = SysUser.builder()
                    .username("chainuser")
                    .nickname("链式用户")
                    .gender("男")
                    .country("中国")
                    .province("北京")
                    .region("朝阳区")
                    .build();

            assertEquals("chainuser", user.getUsername());
            assertEquals("链式用户", user.getNickname());
            assertEquals("男", user.getGender());
            assertEquals("中国", user.getCountry());
            assertEquals("北京", user.getProvince());
            assertEquals("朝阳区", user.getRegion());
        }
    }

    @Nested
    @DisplayName("无参构造器测试")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("无参构造器应创建空对象")
        void noArgsConstructorCreatesEmptyObject() {
            SysUser user = new SysUser();

            assertNull(user.getId());
            assertNull(user.getUsername());
            assertNull(user.getNickname());
            assertNull(user.getPassword());
        }
    }

    @Nested
    @DisplayName("全参构造器测试")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("全参构造器应创建完整对象")
        void allArgsConstructorCreatesCompleteObject() {
            Date now = new Date();
            SysUser user = new SysUser(
                    1L, "fulluser", "pass123", "全用户",
                    "http://avatar.url", "签名", "女",
                    1234567890L, "中国", "广东", "深圳",
                    1, now, now, 1L, 1L, 0,
                    "fulluser@linkx.com", "13800138000"
            );

            assertEquals(1L, user.getId());
            assertEquals("fulluser", user.getUsername());
            assertEquals("pass123", user.getPassword());
            assertEquals("全用户", user.getNickname());
            assertEquals("http://avatar.url", user.getAvatar());
            assertEquals("签名", user.getSignature());
            assertEquals("女", user.getGender());
            assertEquals(1234567890L, user.getBirthday());
            assertEquals("中国", user.getCountry());
            assertEquals("广东", user.getProvince());
            assertEquals("深圳", user.getRegion());
            assertEquals("fulluser@linkx.com", user.getEmail());
            assertEquals(1, user.getStatus());
            assertEquals(now, user.getCreateTime());
            assertEquals(now, user.getUpdateTime());
            assertEquals(1L, user.getCreateBy());
            assertEquals(1L, user.getUpdateBy());
            assertEquals(0, user.getDeleted());
        }
    }

    @Nested
    @DisplayName("Getter/Setter测试")
    class GetterSetterTests {

        @Test
        @DisplayName("setter应正确设置值")
        void settersWorkCorrectly() {
            SysUser user = new SysUser();

            user.setId(100L);
            user.setUsername("setteruser");
            user.setNickname("Setter用户");
            user.setStatus(1);
            user.setDeleted(0);

            assertEquals(100L, user.getId());
            assertEquals("setteruser", user.getUsername());
            assertEquals("Setter用户", user.getNickname());
            assertEquals(1, user.getStatus());
            assertEquals(0, user.getDeleted());
        }

        @Test
        @DisplayName("nullable字段应接受null值")
        void nullableFieldsAcceptNull() {
            SysUser user = new SysUser();

            user.setAvatar(null);
            user.setSignature(null);
            user.setGender(null);
            user.setBirthday(null);
            user.setCountry(null);
            user.setProvince(null);
            user.setRegion(null);

            assertNull(user.getAvatar());
            assertNull(user.getSignature());
            assertNull(user.getGender());
            assertNull(user.getBirthday());
            assertNull(user.getCountry());
            assertNull(user.getProvince());
            assertNull(user.getRegion());
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString应包含关键信息")
        void toStringContainsKeyInfo() {
            SysUser user = SysUser.builder()
                    .id(1L)
                    .username("touser")
                    .nickname("ToString用户")
                    .build();

            String str = user.toString();

            assertTrue(str.contains("1"));
            assertTrue(str.contains("touser"));
            assertTrue(str.contains("ToString用户"));
        }
    }
}
