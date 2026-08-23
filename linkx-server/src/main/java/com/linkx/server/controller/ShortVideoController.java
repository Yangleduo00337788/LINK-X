package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CommentShortVideoDTO;
import com.linkx.server.controller.dto.PublishShortVideoDTO;
import com.linkx.server.controller.dto.UpdateShortVideoDTO;
import com.linkx.server.controller.vo.ShortVideoCommentVO;
import com.linkx.server.controller.vo.ShortVideoPostVO;
import com.linkx.server.service.ShortVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Tag(name = "${openapi.tag.short-video}")
@RequestMapping("/short-video")
@RequiredArgsConstructor
@Validated
public class ShortVideoController {

    private final ShortVideoService shortVideoService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "发布短视频")
    @PostMapping
    @RateLimit(scope = "short-video:publish", value = 10, window = 60)
    public Result<ShortVideoPostVO> publish(
            @Valid @RequestBody PublishShortVideoDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.publish(userId, dto));
    }

    @Operation(summary = "发现流短视频列表")
    @GetMapping
    @RateLimit(scope = "short-video:list", value = 60, window = 60)
    public Result<List<ShortVideoPostVO>> listDiscover(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            @RequestParam(required = false) String q,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listDiscover(userId, parseOptionalId(beforeId), limit, q));
    }

    @Operation(summary = "朋友流短视频列表")
    @GetMapping("/friends")
    public Result<List<ShortVideoPostVO>> listFriends(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listFriends(userId, parseOptionalId(beforeId), limit));
    }

    @Operation(summary = "关注流短视频列表")
    @GetMapping("/following")
    public Result<List<ShortVideoPostVO>> listFollowing(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listFollowing(userId, parseOptionalId(beforeId), limit));
    }

    @Operation(summary = "指定用户短视频列表")
    @GetMapping("/user/{userId}")
    public Result<List<ShortVideoPostVO>> listByUser(
            @PathVariable String userId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listByUser(
                currentUserId, parseId(userId), parseOptionalId(beforeId), limit));
    }

    @Operation(summary = "更新短视频")
    @PutMapping("/{postId}")
    @RateLimit(scope = "short-video:update", value = 20, window = 60)
    public Result<ShortVideoPostVO> update(
            @PathVariable String postId,
            @Valid @RequestBody UpdateShortVideoDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.update(userId, parseId(postId), dto));
    }

    @Operation(summary = "删除短视频")
    @DeleteMapping("/{postId}")
    public Result<Void> delete(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.delete(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "点赞短视频")
    @PostMapping("/{postId}/like")
    @RateLimit(scope = "short-video:like", value = 30, window = 60)
    public Result<Void> like(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.like(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping("/{postId}/like")
    public Result<Void> unlike(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.unlike(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "评论短视频")
    @PostMapping("/{postId}/comment")
    @RateLimit(scope = "short-video:comment", value = 30, window = 60)
    public Result<ShortVideoCommentVO> comment(
            @PathVariable String postId,
            @Valid @RequestBody CommentShortVideoDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.comment(userId, parseId(postId), dto));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable String commentId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.deleteComment(userId, parseId(commentId));
        return Result.success(null);
    }

    @Operation(summary = "关注创作者")
    @PostMapping("/follow/{userId}")
    public Result<Void> follow(@PathVariable String userId, HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.follow(currentUserId, parseId(userId));
        return Result.success(null);
    }

    @Operation(summary = "取消关注创作者")
    @DeleteMapping("/follow/{userId}")
    public Result<Void> unfollow(@PathVariable String userId, HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.unfollow(currentUserId, parseId(userId));
        return Result.success(null);
    }

    @Operation(summary = "记录播放")
    @PostMapping("/{postId}/play")
    public Result<Void> recordPlay(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.recordPlay(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "上传短视频媒体")
    @PostMapping("/upload")
    @RateLimit(scope = "short-video:upload", value = 20, window = 60)
    public Result<String> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.uploadMedia(userId, file));
    }

    @Operation(summary = "鉴权读取视频内容")
    @GetMapping("/{postId}/video/content")
    @RateLimit(scope = "short-video:video-content", value = 60, window = 60)
    public ResponseEntity<InputStreamResource> videoContent(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        var object = shortVideoService.openVideoContent(userId, parseId(postId));
        return MediaStreamResponses.inline(object, "video.mp4");
    }

    @Operation(summary = "鉴权读取封面内容")
    @GetMapping("/{postId}/cover/content")
    public ResponseEntity<InputStreamResource> coverContent(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        var object = shortVideoService.openCoverContent(userId, parseId(postId));
        return MediaStreamResponses.inline(object, "cover.jpg");
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "invalid id");
        }
    }

    private Long parseOptionalId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parseId(id);
    }
}
