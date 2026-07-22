package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.dto.UpdateMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.service.MomentsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ??????
 */
@RestController
@RequestMapping("/moments")
@RequiredArgsConstructor
public class MomentsController {

    private final MomentsService momentsService;
    private final JwtUtils jwtUtils;

    @PostMapping
    @RateLimit(scope = "moments:publish", value = 10, window = 60)
    public Result<MomentsPostVO> publish(
            @Valid @RequestBody PublishMomentsDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.publish(userId, dto));
    }

    @GetMapping
    @RateLimit(scope = "moments:list", value = 60, window = 60)
    public Result<List<MomentsPostVO>> list(
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String q,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.list(userId, parseOptionalId(beforeId), limit, q));
    }

    @GetMapping("/user/{userId}")
    public Result<List<MomentsPostVO>> listByUser(
            @PathVariable String userId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String q,
            HttpServletRequest request) {
        Long currentUserId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.listByUser(
                currentUserId, parseId(userId), parseOptionalId(beforeId), limit, q));
    }

    @PutMapping("/{postId}")
    @RateLimit(scope = "moments:update", value = 20, window = 60)
    public Result<MomentsPostVO> update(
            @PathVariable String postId,
            @Valid @RequestBody UpdateMomentsDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.update(userId, parseId(postId), dto));
    }

    @PostMapping("/{postId}/like")
    @RateLimit(scope = "moments:like", value = 30, window = 60)
    public Result<Void> like(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.like(userId, parseId(postId));
        return Result.success(null);
    }

    @DeleteMapping("/{postId}/like")
    public Result<Void> unlike(
            @PathVariable String postId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.unlike(userId, parseId(postId));
        return Result.success(null);
    }

    @PostMapping("/{postId}/comment")
    @RateLimit(scope = "moments:comment", value = 30, window = 60)
    public Result<MomentsCommentVO> comment(
            @PathVariable String postId,
            @Valid @RequestBody CommentMomentsDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.comment(userId, parseId(postId), dto));
    }

    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(
            @PathVariable String commentId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        momentsService.deleteComment(userId, parseId(commentId));
        return Result.success(null);
    }

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
            throw new com.linkx.server.exception.CustomException(400, "invalid id");
        }
    }

    private Long parseOptionalId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parseId(id);
    }

    @PostMapping("/upload")
    @RateLimit(scope = "moments:upload", value = 30, window = 60)
    public Result<String> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(momentsService.uploadImage(file));
    }
}
