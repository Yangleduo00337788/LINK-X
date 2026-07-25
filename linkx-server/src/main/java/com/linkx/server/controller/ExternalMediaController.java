package com.linkx.server.controller;

import com.linkx.server.common.RateLimit;
import com.linkx.server.service.ExternalMediaProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外链图片 HMAC 代理：&lt;img&gt; 无法带 Authorization，故用短时签名 URL；登录拦截器排除本路径。
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class ExternalMediaController {

    private final ExternalMediaProxyService externalMediaProxyService;

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
}
