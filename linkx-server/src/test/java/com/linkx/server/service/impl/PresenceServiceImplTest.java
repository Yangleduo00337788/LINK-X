package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.admin.AdminEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PresenceServiceImpl 在线状态")
class PresenceServiceImplTest {

    private static final long USER_ID = 100L;
    private static final String DEVICE_ID = "web-1";
    private static final String CONN_ID = "conn-abc";

    @Mock StringRedisTemplate redisTemplate;
    @Mock SetOperations<String, String> setOps;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AdminEventPublisher adminEventPublisher;

    private PresenceServiceImpl service;
    private LinkxProperties props;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        props.getIm().setHeartbeatIntervalSeconds(30);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new PresenceServiceImpl(redisTemplate, props, new ObjectMapper(), adminEventPublisher);
    }

    @Nested
    @DisplayName("基础查询")
    class Query {
        @Test
        @DisplayName("getInstanceId 非空")
        void instanceId() {
            assertNotNull(service.getInstanceId());
            assertFalse(service.getInstanceId().isBlank());
        }

        @Test
        @DisplayName("isOnline 有连接")
        void isOnlineTrue() {
            when(setOps.size(PresenceServiceImpl.CONN_KEY_PREFIX + USER_ID)).thenReturn(2L);
            assertTrue(service.isOnline(USER_ID));
        }

        @Test
        @DisplayName("isOnline 无连接")
        void isOnlineFalse() {
            when(setOps.size(anyString())).thenReturn(0L);
            assertFalse(service.isOnline(USER_ID));
            assertFalse(service.isOnline(null));
        }

        @Test
        @DisplayName("onlineDeviceIds 解析设备")
        void onlineDeviceIds() {
            String instanceId = service.getInstanceId();
            Set<String> members = new LinkedHashSet<>();
            members.add(instanceId + ":phone-1:conn1");
            members.add(instanceId + ":web-2:conn2");
            when(setOps.members(PresenceServiceImpl.CONN_KEY_PREFIX + USER_ID)).thenReturn(members);

            Set<String> devices = service.onlineDeviceIds(USER_ID);

            assertEquals(Set.of("phone-1", "web-2"), devices);
        }

        @Test
        @DisplayName("isDeviceOnline 命中")
        void isDeviceOnlineTrue() {
            String instanceId = service.getInstanceId();
            when(setOps.members(PresenceServiceImpl.CONN_KEY_PREFIX + USER_ID))
                    .thenReturn(Set.of(instanceId + ":" + DEVICE_ID + ":x"));
            assertTrue(service.isDeviceOnline(USER_ID, DEVICE_ID));
        }
    }

    @Nested
    @DisplayName("markOnline / markOffline")
    class Mark {
        @Test
        @DisplayName("markOnline 首连发布上线事件")
        void markOnlineFirstConnection() {
            when(setOps.members(anyString())).thenReturn(Set.of());
            when(redisTemplate.execute(any(), anyList(), any(), any()))
                    .thenReturn(List.of(0L, 1L));

            service.markOnline(USER_ID, DEVICE_ID, CONN_ID);

            verify(redisTemplate).convertAndSend(eq(PresenceServiceImpl.EVENTS_CHANNEL), anyString());
            verify(adminEventPublisher).publish(eq("device_presence"), eq(USER_ID), contains("online"));
            verify(valueOps).set(startsWith(PresenceServiceImpl.HB_KEY_PREFIX), eq("1"), any());
        }

        @Test
        @DisplayName("markOffline 末连发布下线事件")
        void markOfflineLastConnection() {
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(0L);
            when(setOps.members(anyString())).thenReturn(Set.of());

            service.markOffline(USER_ID, DEVICE_ID, CONN_ID);

            verify(redisTemplate).convertAndSend(eq(PresenceServiceImpl.EVENTS_CHANNEL), anyString());
            verify(adminEventPublisher).publish(eq("device_presence"), eq(USER_ID), contains("false"));
        }

        @Test
        @DisplayName("touch 刷新 TTL")
        void touch() {
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
            service.touch(USER_ID);
            verify(redisTemplate).execute(any(), anyList(), any());
            verify(valueOps).set(startsWith(PresenceServiceImpl.HB_KEY_PREFIX), eq("1"), any());
        }
    }

    @Nested
    @DisplayName("实例维护")
    class Instance {
        @Test
        @DisplayName("refreshInstanceHeartbeat 写入心跳")
        void refreshHeartbeat() {
            service.refreshInstanceHeartbeat();
            verify(valueOps).set(startsWith(PresenceServiceImpl.HB_KEY_PREFIX), eq("1"), any());
            verify(setOps).add(PresenceServiceImpl.INSTANCES_KEY, service.getInstanceId());
        }

        @Test
        @DisplayName("broadcastPresence 发布事件")
        void broadcast() {
            service.broadcastPresence(USER_ID, true);
            verify(redisTemplate).convertAndSend(eq(PresenceServiceImpl.EVENTS_CHANNEL), anyString());
        }

        @Test
        @DisplayName("clearLocalPresenceOnShutdown 清理本实例")
        void shutdownCleanup() {
            String byInstKey = PresenceServiceImpl.BY_INST_KEY_PREFIX + service.getInstanceId();
            when(setOps.members(byInstKey)).thenReturn(Set.of(USER_ID + "\t" + service.getInstanceId() + ":d:c"));
            when(setOps.size(anyString())).thenReturn(0L);

            service.clearLocalPresenceOnShutdown();

            verify(redisTemplate).delete(byInstKey);
            verify(redisTemplate).delete(PresenceServiceImpl.HB_KEY_PREFIX + service.getInstanceId());
            verify(setOps).remove(PresenceServiceImpl.INSTANCES_KEY, service.getInstanceId());
        }

        @Test
        @DisplayName("sweepDeadInstances 清扫无心跳实例")
        void sweepDead() {
            String deadInstance = "dead-instance-id";
            when(setOps.members(PresenceServiceImpl.INSTANCES_KEY)).thenReturn(Set.of(deadInstance, service.getInstanceId()));
            when(redisTemplate.hasKey(PresenceServiceImpl.HB_KEY_PREFIX + deadInstance)).thenReturn(false);
            String byInstKey = PresenceServiceImpl.BY_INST_KEY_PREFIX + deadInstance;
            when(setOps.members(byInstKey)).thenReturn(Set.of());
            when(setOps.size(anyString())).thenReturn(0L);

            service.sweepDeadInstances();

            verify(redisTemplate).delete(byInstKey);
            verify(setOps).remove(PresenceServiceImpl.INSTANCES_KEY, deadInstance);
        }
    }
}
