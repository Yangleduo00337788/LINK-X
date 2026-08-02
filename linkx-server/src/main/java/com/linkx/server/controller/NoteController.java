package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SaveNoteDTO;
import com.linkx.server.controller.vo.NoteFileUploadVO;
import com.linkx.server.controller.vo.NoteVO;
import com.linkx.server.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Note controller
 */
@RestController
@Tag(name = "${openapi.tag.note}")
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "获取笔记列表")
    @GetMapping
    public Result<List<NoteVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.list(userId));
    }

    @Operation(summary = "获取笔记详情")
    @GetMapping("/{noteId}")
    public Result<NoteVO> get(
            @PathVariable String noteId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.get(userId, parseId(noteId)));
    }

    @Operation(summary = "创建笔记")
    @PostMapping
    public Result<NoteVO> create(
            @Valid @RequestBody SaveNoteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.create(userId, dto));
    }

    @Operation(summary = "更新笔记")
    @PutMapping("/{noteId}")
    public Result<NoteVO> update(
            @PathVariable String noteId,
            @Valid @RequestBody SaveNoteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.update(userId, parseId(noteId), dto));
    }

    @Operation(summary = "删除笔记")
    @DeleteMapping("/{noteId}")
    public Result<Void> delete(
            @PathVariable String noteId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        noteService.delete(userId, parseId(noteId));
        return Result.success(null);
    }

    @Operation(summary = "上传笔记附件")
    @PostMapping("/upload")
    @RateLimit(scope = "notes:upload", value = 30, window = 60)
    public Result<NoteFileUploadVO> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.upload(userId, file));
    }

    @Operation(summary = "解析笔记媒体 URL")
    @GetMapping("/media-url")
    public Result<String> resolveMedia(
            @RequestParam("key") String key,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.resolveMediaUrl(userId, key));
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "invalid id");
        }
    }
}
