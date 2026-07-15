package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SaveNoteDTO;
import com.linkx.server.controller.vo.NoteVO;
import com.linkx.server.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记控制器
 */
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final JwtUtils jwtUtils;

    /**
     * 获取笔记列表
     */
    @GetMapping
    public Result<List<NoteVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.list(userId));
    }

    /**
     * 获取单条笔记
     */
    @GetMapping("/{noteId}")
    public Result<NoteVO> get(
            @PathVariable String noteId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.get(userId, parseId(noteId)));
    }

    /**
     * 创建笔记
     */
    @PostMapping
    public Result<NoteVO> create(
            @Valid @RequestBody SaveNoteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.create(userId, dto));
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{noteId}")
    public Result<NoteVO> update(
            @PathVariable String noteId,
            @Valid @RequestBody SaveNoteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(noteService.update(userId, parseId(noteId), dto));
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{noteId}")
    public Result<Void> delete(
            @PathVariable String noteId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        noteService.delete(userId, parseId(noteId));
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
