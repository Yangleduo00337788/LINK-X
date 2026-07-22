package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CreateGroupAssetDTO;
import com.linkx.server.controller.vo.GroupAssetVO;
import com.linkx.server.service.GroupAssetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/group/{conversationId}/assets")
@RequiredArgsConstructor
public class GroupAssetController {

    private final GroupAssetService groupAssetService;
    private final JwtUtils jwtUtils;

    @GetMapping
    public Result<List<GroupAssetVO>> list(
            @PathVariable String conversationId,
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupAssetService.list(userId, parseId(conversationId), type));
    }

    @PostMapping
    public Result<GroupAssetVO> create(
            @PathVariable String conversationId,
            @Valid @RequestBody CreateGroupAssetDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupAssetService.create(userId, parseId(conversationId), dto));
    }

    @PostMapping("/upload")
    @RateLimit(scope = "group:asset-upload", value = 30, window = 60)
    public Result<GroupAssetVO> upload(
            @PathVariable String conversationId,
            @RequestParam("type") String type,
            @RequestParam(value = "album", required = false) String album,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupAssetService.upload(userId, parseId(conversationId), type, file, album));
    }

    @DeleteMapping("/{assetId}")
    public Result<Void> delete(
            @PathVariable String conversationId,
            @PathVariable String assetId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupAssetService.delete(userId, parseId(conversationId), parseId(assetId));
        return Result.success(null);
    }

    /** 鉴权中转下载群文件/相册（群成员） */
    @GetMapping("/{assetId}/content")
    public ResponseEntity<InputStreamResource> downloadContent(
            @PathVariable String conversationId,
            @PathVariable String assetId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long convId = parseId(conversationId);
        Long id = parseId(assetId);
        String name = groupAssetService.getAssetFileName(userId, convId, id);
        var object = groupAssetService.openAssetContent(userId, convId, id);
        return MediaStreamResponses.download(object, name);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
