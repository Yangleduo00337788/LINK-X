package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RateLimit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysBanner;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysBannerMapper;
import com.linkx.server.service.ExternalMediaProxyService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 媒体代理：外链 HMAC；用户头像 / Banner 同源流式输出（&lt;img&gt; 无法带 Authorization）。
 */
@RestController
@Tag(name = "${openapi.tag.media}")
@RequestMapping("/media")
@RequiredArgsConstructor
public class ExternalMediaController {

    private final ExternalMediaProxyService externalMediaProxyService;
    private final SysUserMapper sysUserMapper;
    private final SysBannerMapper sysBannerMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;

    @GetMapping("/external")
    @RateLimit(scope = "media:external", value = 120, window = 60, byUser = false)
    public ResponseEntity<byte[]> proxyExternal(
            @RequestParam("u") String url,
            @RequestParam("e") long expiresEpochSec,
            @RequestParam("s") String signature) {
        ExternalMediaProxyService.ProxiedImage image =
                externalMediaProxyService.fetch(url, expiresEpochSec, signature);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(image.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.body());
    }

    @Operation(summary = "用户头像（同源流，供 img 标签加载）")
    @GetMapping("/avatars/{userId}")
    @RateLimit(scope = "media:avatar", value = 180, window = 60, byUser = false)
    public ResponseEntity<?> userAvatar(@PathVariable Long userId) {
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null || !StringUtils.hasText(user.getAvatar())) {
            return ResponseEntity.notFound().build();
        }
        String avatar = user.getAvatar().trim();
        if (mediaUrlService.isExternalHttpUrl(avatar)
                || avatar.startsWith("data:")
                || avatar.startsWith("blob:")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(avatar)).build();
        }
        if (avatar.startsWith("/")) {
            return ResponseEntity.notFound().build();
        }
        try {
            FileStorageService.StoredObject object = fileStorageService.openObject(avatar);
            return MediaStreamResponses.inline(object, "avatar");
        } catch (Exception e) {
            String signed = mediaUrlService.resolveAvatar(avatar);
            if (StringUtils.hasText(signed) && (signed.startsWith("http://") || signed.startsWith("https://"))) {
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signed)).build();
            }
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "运营 Banner 图片（同源流，供 img 标签加载）")
    @GetMapping("/banners/{id}")
    @RateLimit(scope = "media:banner", value = 180, window = 60, byUser = false)
    public ResponseEntity<?> bannerImage(@PathVariable Long id) {
        SysBanner banner = sysBannerMapper.selectOneById(id);
        if (banner == null
                || (banner.getDeleted() != null && banner.getDeleted() == 1)
                || !StringUtils.hasText(banner.getImageUrl())) {
            return ResponseEntity.notFound().build();
        }
        String image = banner.getImageUrl().trim();
        if (mediaUrlService.isExternalHttpUrl(image)
                || image.startsWith("data:")
                || image.startsWith("blob:")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(image)).build();
        }
        if (image.startsWith("/")) {
            return ResponseEntity.notFound().build();
        }
        try {
            FileStorageService.StoredObject object = fileStorageService.openObject(image);
            return MediaStreamResponses.inline(object, "banner");
        } catch (Exception e) {
            String signed = mediaUrlService.resolveAvatar(image);
            if (StringUtils.hasText(signed) && (signed.startsWith("http://") || signed.startsWith("https://"))) {
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signed)).build();
            }
            return ResponseEntity.notFound().build();
        }
    }
}
