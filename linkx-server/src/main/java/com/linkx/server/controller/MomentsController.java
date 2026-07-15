package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.service.MomentsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 朋友圈控制器
 */
@RestController
@RequestMapping("/moments")
@RequiredArgsConstructor
public class MomentsController {

    private final MomentsService momentsService;
    private final JwtUtils jwtUtils;

    /**
     * 发布动态
     */
    @PostMapping
    @RateLimit(scope = "moments:publish", value = 10, window = 60)
    public Result<MomentsPostVO> publish(
            @Valid @RequestBody PublishMomentsDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.publish(userId, dto));
    }

    /**
     * 获取朋友圈动态列表
     */
    @GetMapping
    @RateLimit(scope = "moments:list", value = 60, window = 60)
    public Result<List<MomentsPostVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.list(userId));
    }

    /**
     * 获取指定用户的动态列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<MomentsPostVO>> listByUser(
            @PathVariable String userId,
            HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.listByUser(currentUserId, parseId(userId)));
    }

    /**
     * 点赞动态
     */
    @PostMapping("/{postId}/like")
    @RateLimit(scope = "moments:like", value = 30, window = 60)
    public Result<Void> like(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.like(userId, parseId(postId));
        return Result.success(null);
    }

    /**
     * 取消点赞
     */
    @DeleteMapping("/{postId}/like")
    public Result<Void> unlike(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.unlike(userId, parseId(postId));
        return Result.success(null);
    }

    /**
     * 评论动态
     */
    @PostMapping("/{postId}/comment")
    @RateLimit(scope = "moments:comment", value = 30, window = 60)
    public Result<MomentsCommentVO> comment(
            @PathVariable String postId,
            @Valid @RequestBody CommentMomentsDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.comment(userId, parseId(postId), dto));
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(
            @PathVariable String commentId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.deleteComment(userId, parseId(commentId));
        return Result.success(null);
    }

    /**
     * 删除动态
     */
    @DeleteMapping("/{postId}")
    public Result<Void> delete(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.delete(userId, parseId(postId));
        return Result.success(null);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
