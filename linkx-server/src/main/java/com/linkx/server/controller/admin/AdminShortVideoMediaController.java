package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.service.admin.AdminShortVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端短视频媒体同源代理：供 {@code <img>} / {@code <video>} 通过 Cookie 加载，无需 API 签名头。
 */
@Tag(name = "管理端-短视频媒体")
@RestController
@RequestMapping("/media/admin-short-video")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminShortVideoMediaController {

    private final AdminShortVideoService adminShortVideoService;

    @Operation(summary = "短视频封面（管理端）")
    @GetMapping("/{postId}/cover")
    @RequirePermission("admin:short-video:view")
    public ResponseEntity<InputStreamResource> coverContent(@PathVariable Long postId) {
        var object = adminShortVideoService.openCoverContent(postId);
        return MediaStreamResponses.inline(object, "cover.jpg", null);
    }

    @Operation(summary = "短视频视频流（管理端）")
    @GetMapping("/{postId}/video")
    @RequirePermission("admin:short-video:view")
    public ResponseEntity<InputStreamResource> videoContent(
            @PathVariable Long postId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        var object = adminShortVideoService.openVideoContent(postId);
        return MediaStreamResponses.inline(object, "video.mp4", range);
    }
}
