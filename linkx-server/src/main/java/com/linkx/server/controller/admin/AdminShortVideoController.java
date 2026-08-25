package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminShortVideoCommentQueryDTO;
import com.linkx.server.controller.admin.dto.AdminShortVideoPostQueryDTO;
import com.linkx.server.controller.admin.vo.AdminShortVideoCommentVO;
import com.linkx.server.controller.admin.vo.AdminShortVideoPostVO;
import com.linkx.server.service.admin.AdminShortVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-短视频管理")
@RestController
@RequestMapping("/admin/short-video")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminShortVideoController {

    private final AdminShortVideoService adminShortVideoService;

    @Operation(summary = "短视频作品列表")
    @GetMapping("/posts")
    @RequirePermission("admin:short-video:list")
    public Result<PageResultVO<AdminShortVideoPostVO>> listPosts(@Valid AdminShortVideoPostQueryDTO query) {
        return Result.success(adminShortVideoService.listPosts(query));
    }

    @Operation(summary = "短视频作品详情")
    @GetMapping("/posts/{postId}")
    @RequirePermission("admin:short-video:list")
    public Result<AdminShortVideoPostVO> postDetail(@PathVariable Long postId) {
        return Result.success(adminShortVideoService.postDetail(postId));
    }

    @Operation(summary = "下架短视频作品")
    @AuditAction(operationType = "DELETE", description = "下架短视频作品")
    @DeleteMapping("/posts/{postId}")
    @RequirePermission("admin:short-video:delete")
    public Result<Void> deletePost(@PathVariable Long postId) {
        adminShortVideoService.deletePost(postId);
        return Result.success(null);
    }

    @Operation(summary = "重新加入转码队列")
    @AuditAction(operationType = "UPDATE", description = "短视频重新转码")
    @PostMapping("/posts/{postId}/retranscode")
    @RequirePermission("admin:short-video:view")
    public Result<Void> retranscodePost(@PathVariable Long postId) {
        adminShortVideoService.enqueueRetranscode(postId);
        return Result.success(null);
    }

    @Operation(summary = "短视频评论列表")
    @GetMapping("/comments")
    @RequirePermission("admin:short-video:list")
    public Result<PageResultVO<AdminShortVideoCommentVO>> listComments(@Valid AdminShortVideoCommentQueryDTO query) {
        return Result.success(adminShortVideoService.listComments(query));
    }

    @Operation(summary = "删除短视频评论")
    @AuditAction(operationType = "DELETE", description = "删除短视频评论")
    @DeleteMapping("/comments/{commentId}")
    @RequirePermission("admin:short-video:delete")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        adminShortVideoService.deleteComment(commentId);
        return Result.success(null);
    }

    @Operation(summary = "预览短视频视频流")
    @GetMapping("/posts/{postId}/video/content")
    @RequirePermission("admin:short-video:view")
    public ResponseEntity<InputStreamResource> videoContent(
            @PathVariable Long postId,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        var object = adminShortVideoService.openVideoContent(postId);
        return MediaStreamResponses.inline(object, "video.mp4", range);
    }

    @Operation(summary = "预览短视频封面")
    @GetMapping("/posts/{postId}/cover/content")
    @RequirePermission("admin:short-video:view")
    public ResponseEntity<InputStreamResource> coverContent(@PathVariable Long postId) {
        var object = adminShortVideoService.openCoverContent(postId);
        return MediaStreamResponses.inline(object, "cover.jpg", null);
    }
}
