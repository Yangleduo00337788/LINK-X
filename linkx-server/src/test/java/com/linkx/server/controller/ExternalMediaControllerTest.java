package com.linkx.server.controller;

import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysBanner;
import com.linkx.server.entity.admin.SysOpsActivity;
import com.linkx.server.entity.admin.SysOpsRecommend;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysBannerMapper;
import com.linkx.server.mapper.admin.SysOpsActivityMapper;
import com.linkx.server.mapper.admin.SysOpsRecommendMapper;
import com.linkx.server.service.ExternalMediaProxyService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExternalMediaController 媒体代理")
class ExternalMediaControllerTest {

    @Mock ExternalMediaProxyService externalMediaProxyService;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysBannerMapper sysBannerMapper;
    @Mock SysOpsRecommendMapper sysOpsRecommendMapper;
    @Mock SysOpsActivityMapper sysOpsActivityMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;

    private ExternalMediaController controller;

    @BeforeEach
    void setUp() {
        controller = new ExternalMediaController(
                externalMediaProxyService, sysUserMapper, sysBannerMapper,
                sysOpsRecommendMapper, sysOpsActivityMapper, fileStorageService, mediaUrlService
        );
    }

    @Nested
    @DisplayName("proxyExternal")
    class ProxyExternal {
        @Test
        @DisplayName("代理外链图片")
        void ok() {
            when(externalMediaProxyService.fetch(eq("https://cdn/a.jpg"), eq(1L), eq("sig")))
                    .thenReturn(new ExternalMediaProxyService.ProxiedImage(
                            new byte[]{1, 2, 3}, "image/png"));

            ResponseEntity<byte[]> resp = controller.proxyExternal("https://cdn/a.jpg", 1L, "sig");
            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertArrayEquals(new byte[]{1, 2, 3}, resp.getBody());
        }

        @Test
        @DisplayName("非法 Content-Type 回退 jpeg")
        void badContentType() {
            when(externalMediaProxyService.fetch(anyString(), anyLong(), anyString()))
                    .thenReturn(new ExternalMediaProxyService.ProxiedImage(
                            new byte[]{9}, "not-a-type!!!"));

            ResponseEntity<byte[]> resp = controller.proxyExternal("u", 1L, "s");
            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertNotNull(resp.getHeaders().getContentType());
        }
    }

    @Nested
    @DisplayName("userAvatar")
    class Avatar {
        @Test
        @DisplayName("用户不存在 404")
        void missing() {
            when(sysUserMapper.selectOneById(1L)).thenReturn(null);
            assertEquals(HttpStatus.NOT_FOUND, controller.userAvatar(1L).getStatusCode());
        }

        @Test
        @DisplayName("外链头像 302")
        void externalRedirect() {
            when(sysUserMapper.selectOneById(1L)).thenReturn(
                    SysUser.builder().id(1L).avatar("https://ext/a.png").build());
            when(mediaUrlService.isExternalHttpUrl("https://ext/a.png")).thenReturn(true);

            ResponseEntity<?> resp = controller.userAvatar(1L);
            assertEquals(HttpStatus.FOUND, resp.getStatusCode());
        }

        @Test
        @DisplayName("本地对象流式输出")
        void streamLocal() {
            when(sysUserMapper.selectOneById(1L)).thenReturn(
                    SysUser.builder().id(1L).avatar("2026/a.png").build());
            when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
            when(fileStorageService.openObject("2026/a.png")).thenReturn(
                    new FileStorageService.StoredObject(
                            new ByteArrayInputStream(new byte[]{1}), "image/png", 1L, "2026/a.png"));

            ResponseEntity<?> resp = controller.userAvatar(1L);
            assertEquals(HttpStatus.OK, resp.getStatusCode());
        }

        @Test
        @DisplayName("打开失败时签名回退")
        void fallbackSigned() {
            when(sysUserMapper.selectOneById(1L)).thenReturn(
                    SysUser.builder().id(1L).avatar("2026/a.png").build());
            when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
            when(fileStorageService.openObject(anyString())).thenThrow(new RuntimeException("minio down"));
            when(mediaUrlService.resolveAvatar("2026/a.png")).thenReturn("https://signed/a.png");

            ResponseEntity<?> resp = controller.userAvatar(1L);
            assertEquals(HttpStatus.FOUND, resp.getStatusCode());
        }

        @Test
        @DisplayName("斜杠路径 404")
        void slashPath() {
            when(sysUserMapper.selectOneById(1L)).thenReturn(
                    SysUser.builder().id(1L).avatar("/static/x").build());
            when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
            assertEquals(HttpStatus.NOT_FOUND, controller.userAvatar(1L).getStatusCode());
        }
    }

    @Nested
    @DisplayName("banner / recommend / activity")
    class OpsImages {
        @Test
        @DisplayName("banner 本地流")
        void bannerOk() {
            when(sysBannerMapper.selectOneById(2L)).thenReturn(
                    SysBanner.builder().id(2L).imageUrl("b.png").deleted(0).build());
            when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
            when(fileStorageService.openObject("b.png")).thenReturn(
                    new FileStorageService.StoredObject(
                            new ByteArrayInputStream(new byte[]{1}), "image/png", 1L, "b.png"));

            assertEquals(HttpStatus.OK, controller.bannerImage(2L).getStatusCode());
        }

        @Test
        @DisplayName("banner 已删除 404")
        void bannerDeleted() {
            when(sysBannerMapper.selectOneById(2L)).thenReturn(
                    SysBanner.builder().id(2L).imageUrl("b.png").deleted(1).build());
            assertEquals(HttpStatus.NOT_FOUND, controller.bannerImage(2L).getStatusCode());
        }

        @Test
        @DisplayName("recommend 外链 302")
        void recommendExternal() {
            when(sysOpsRecommendMapper.selectOneById(3L)).thenReturn(
                    SysOpsRecommend.builder().id(3L).imageUrl("https://x/r.png").deleted(0).build());
            when(mediaUrlService.isExternalHttpUrl("https://x/r.png")).thenReturn(true);
            assertEquals(HttpStatus.FOUND, controller.recommendImage(3L).getStatusCode());
        }

        @Test
        @DisplayName("activity 打开失败且无签名 404")
        void activityFallback404() {
            when(sysOpsActivityMapper.selectOneById(4L)).thenReturn(
                    SysOpsActivity.builder().id(4L).coverUrl("c.png").deleted(0).build());
            when(mediaUrlService.isExternalHttpUrl(anyString())).thenReturn(false);
            when(fileStorageService.openObject(anyString())).thenThrow(new RuntimeException("x"));
            when(mediaUrlService.resolveAvatar(anyString())).thenReturn(null);
            assertEquals(HttpStatus.NOT_FOUND, controller.activityCover(4L).getStatusCode());
        }

        @Test
        @DisplayName("recommend 不存在 404")
        void recommendMissing() {
            when(sysOpsRecommendMapper.selectOneById(9L)).thenReturn(null);
            assertEquals(HttpStatus.NOT_FOUND, controller.recommendImage(9L).getStatusCode());
        }
    }
}
