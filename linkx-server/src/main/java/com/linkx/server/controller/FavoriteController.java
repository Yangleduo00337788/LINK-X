package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SaveFavoriteDTO;
import com.linkx.server.controller.dto.SaveFavoriteTagDTO;
import com.linkx.server.controller.vo.FavoriteStorageVO;
import com.linkx.server.controller.vo.FavoriteTagVO;
import com.linkx.server.controller.vo.FavoriteVO;
import com.linkx.server.service.FavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "${openapi.tag.favorite}")
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "获取收藏列表")
    @GetMapping
    public Result<List<FavoriteVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.list(userId));
    }

    @Operation(summary = "查询收藏存储空间")
    @GetMapping("/storage")
    public Result<FavoriteStorageVO> storage(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.getStorage(userId));
    }

    @Operation(summary = "获取收藏标签列表")
    @GetMapping("/tags")
    public Result<List<FavoriteTagVO>> tags(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.listTags(userId));
    }

    @Operation(summary = "创建收藏标签")
    @PostMapping("/tags")
    public Result<FavoriteTagVO> createTag(
            @Valid @RequestBody SaveFavoriteTagDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.createTag(userId, dto));
    }

    @Operation(summary = "删除收藏标签")
    @DeleteMapping("/tags/{tagId}")
    public Result<Void> deleteTag(@PathVariable String tagId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        favoriteService.deleteTag(userId, parseId(tagId));
        return Result.success(null);
    }

    @Operation(summary = "获取收藏详情")
    @GetMapping("/{favoriteId}")
    public Result<FavoriteVO> get(@PathVariable String favoriteId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.get(userId, parseId(favoriteId)));
    }

    @Operation(summary = "新增收藏")
    @PostMapping
    public Result<FavoriteVO> create(@Valid @RequestBody SaveFavoriteDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.create(userId, dto));
    }

    @Operation(summary = "更新收藏")
    @PutMapping("/{favoriteId}")
    public Result<FavoriteVO> update(
            @PathVariable String favoriteId,
            @Valid @RequestBody SaveFavoriteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(favoriteService.update(userId, parseId(favoriteId), dto));
    }

    @Operation(summary = "删除收藏")
    @DeleteMapping("/{favoriteId}")
    public Result<Void> delete(@PathVariable String favoriteId, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        favoriteService.delete(userId, parseId(favoriteId));
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
