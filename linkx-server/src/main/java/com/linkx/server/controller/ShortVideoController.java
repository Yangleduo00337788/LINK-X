package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.dto.CommentShortVideoDTO;
import com.linkx.server.controller.dto.PublishShortVideoDTO;
import com.linkx.server.controller.dto.ReportShortVideoDTO;
import com.linkx.server.controller.dto.ShareShortVideoChatDTO;
import com.linkx.server.controller.dto.UpdateShortVideoDTO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.controller.vo.ShortVideoAuthorVO;
import com.linkx.server.controller.vo.ShortVideoCommentVO;
import com.linkx.server.controller.vo.ShortVideoFollowingUserVO;
import com.linkx.server.controller.vo.ShortVideoPostVO;
import com.linkx.server.controller.vo.ShortVideoTopicVO;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.ShortVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
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
    private final ChatService chatService;
    private final ImMessagePushService imMessagePushService;
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

    @Operation(summary = "获取单条短视频")
    @GetMapping("/{postId}")
    @RateLimit(scope = "short-video:get", value = 60, window = 60)
    public Result<ShortVideoPostVO> get(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.getPost(userId, parseId(postId)));
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

    @Operation(summary = "热门话题榜")
    @GetMapping("/topics/hot")
    @RateLimit(scope = "short-video:topics-hot", value = 60, window = 60)
    public Result<List<ShortVideoTopicVO>> listHotTopics(
            @RequestParam(required = false) @Min(1) @Max(30) Integer limit,
            HttpServletRequest request) {
        AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listHotTopics(limit));
    }

    @Operation(summary = "短视频热榜")
    @GetMapping("/hot")
    @RateLimit(scope = "short-video:hot", value = 60, window = 60)
    public Result<List<ShortVideoPostVO>> listHotVideos(
            @RequestParam(required = false) @Min(1) @Max(30) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listHotVideos(userId, limit));
    }

    @Operation(summary = "话题广场列表")
    @GetMapping("/topics")
    @RateLimit(scope = "short-video:topics", value = 60, window = 60)
    public Result<PageResultVO<ShortVideoTopicVO>> listTopics(
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listTopicPlaza(page, limit));
    }

    @Operation(summary = "话题详情")
    @GetMapping("/topics/{name}")
    @RateLimit(scope = "short-video:topic-detail", value = 60, window = 60)
    public Result<ShortVideoTopicVO> getTopic(
            @PathVariable String name,
            HttpServletRequest request) {
        AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.getTopic(name));
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

    @Operation(summary = "我关注的创作者列表")
    @GetMapping("/following/users")
    @RateLimit(scope = "short-video:following-users", value = 60, window = 60)
    public Result<List<ShortVideoFollowingUserVO>> listFollowingUsers(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listFollowingUsers(userId, parseOptionalId(beforeId), limit));
    }

    @Operation(summary = "我关注的创作者数量")
    @GetMapping("/following/count")
    @RateLimit(scope = "short-video:following-count", value = 60, window = 60)
    public Result<Integer> countFollowingUsers(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.countFollowingUsers(userId));
    }

    @Operation(summary = "我的收藏短视频列表")
    @GetMapping("/favorites")
    @RateLimit(scope = "short-video:favorites", value = 60, window = 60)
    public Result<List<ShortVideoPostVO>> listFavorites(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listFavorites(userId, parseOptionalId(beforeId), limit));
    }

    @Operation(summary = "我的点赞短视频列表")
    @GetMapping("/likes")
    @RateLimit(scope = "short-video:likes", value = 60, window = 60)
    public Result<List<ShortVideoPostVO>> listLikes(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listLikes(userId, parseOptionalId(beforeId), limit));
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

    @Operation(summary = "创作者资料")
    @GetMapping("/user/{userId}/profile")
    @RateLimit(scope = "short-video:author-profile", value = 60, window = 60)
    public Result<ShortVideoAuthorVO> getAuthorProfile(
            @PathVariable String userId,
            HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.getAuthorProfile(currentUserId, parseId(userId)));
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

    @Operation(summary = "收藏短视频")
    @PostMapping("/{postId}/favorite")
    @RateLimit(scope = "short-video:favorite", value = 30, window = 60)
    public Result<Void> favorite(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.favorite(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{postId}/favorite")
    public Result<Void> unfavorite(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.unfavorite(userId, parseId(postId));
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

    @Operation(summary = "短视频评论列表")
    @GetMapping("/{postId}/comments")
    @RateLimit(scope = "short-video:comments", value = 60, window = 60)
    public Result<List<ShortVideoCommentVO>> listComments(
            @PathVariable String postId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(shortVideoService.listComments(
                userId, parseId(postId), parseOptionalId(beforeId), limit));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable String commentId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.deleteComment(userId, parseId(commentId));
        return Result.success(null);
    }

    @Operation(summary = "点赞评论")
    @PostMapping("/comment/{commentId}/like")
    @RateLimit(scope = "short-video:comment-like", value = 60, window = 60)
    public Result<Void> likeComment(@PathVariable String commentId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.likeComment(userId, parseId(commentId));
        return Result.success(null);
    }

    @Operation(summary = "取消点赞评论")
    @DeleteMapping("/comment/{commentId}/like")
    public Result<Void> unlikeComment(@PathVariable String commentId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.unlikeComment(userId, parseId(commentId));
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

    @Operation(summary = "标记不感兴趣")
    @PostMapping("/{postId}/not-interested")
    @RateLimit(scope = "short-video:not-interested", value = 30, window = 60)
    public Result<Void> markNotInterested(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.markNotInterested(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "屏蔽创作者")
    @PostMapping("/block/{userId}")
    @RateLimit(scope = "short-video:block-author", value = 20, window = 60)
    public Result<Void> blockAuthor(@PathVariable String userId, HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.blockAuthor(currentUserId, parseId(userId));
        return Result.success(null);
    }

    @Operation(summary = "举报短视频")
    @PostMapping("/{postId}/report")
    @RateLimit(scope = "short-video:report", value = 20, window = 60)
    public Result<Void> reportPost(@PathVariable String postId,
                                   @Valid @RequestBody ReportShortVideoDTO dto,
                                   HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.reportPost(userId, parseId(postId), dto);
        return Result.success(null);
    }

    @Operation(summary = "记录播放")
    @PostMapping("/{postId}/play")
    public Result<Void> recordPlay(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.recordPlay(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "记录分享")
    @PostMapping("/{postId}/share")
    @RateLimit(scope = "short-video:share", value = 30, window = 60)
    public Result<Void> recordShare(@PathVariable String postId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        shortVideoService.recordShare(userId, parseId(postId));
        return Result.success(null);
    }

    @Operation(summary = "分享短视频到聊天")
    @PostMapping("/{postId}/share-chat")
    @RateLimit(scope = "short-video:share-chat", value = 20, window = 60)
    public Result<List<MessageVO>> shareToChat(
            @PathVariable String postId,
            @Valid @RequestBody ShareShortVideoChatDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        List<Long> conversationIds = dto.getConversationIds().stream()
                .map(this::parseId)
                .distinct()
                .toList();
        List<MessageVO> messages = chatService.postShortVideoShareMessages(
                userId, parseId(postId), conversationIds, dto.getLeaveMessage());
        for (MessageVO vo : messages) {
            imMessagePushService.pushToConversationMembers(vo, userId, vo.getClientMsgId());
        }
        return Result.success(messages);
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
        return MediaStreamResponses.inline(object, "video.mp4", request.getHeader(HttpHeaders.RANGE));
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

    @Operation(summary = "鉴权读取评论图片")
    @GetMapping("/comment/{commentId}/image/content")
    @RateLimit(scope = "short-video:comment-image", value = 60, window = 60)
    public ResponseEntity<InputStreamResource> commentImageContent(
            @PathVariable String commentId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        var object = shortVideoService.openCommentImageContent(userId, parseId(commentId));
        return MediaStreamResponses.inline(object, "comment.jpg");
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
