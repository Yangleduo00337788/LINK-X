package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CreateDriveFolderDTO;
import com.linkx.server.controller.dto.CreateDriveShareDTO;
import com.linkx.server.controller.dto.DriveBatchDTO;
import com.linkx.server.controller.dto.DriveTagDTO;
import com.linkx.server.controller.dto.UpdateDriveItemDTO;
import com.linkx.server.controller.vo.DriveActivityVO;
import com.linkx.server.controller.vo.DriveItemVO;
import com.linkx.server.controller.vo.DriveShareVO;
import com.linkx.server.controller.vo.DriveStorageVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.CloudDriveService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "${openapi.tag.cloud-drive}")
@RequestMapping("/cloud")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class CloudDriveController {

    private final CloudDriveService cloudDriveService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "查询云盘存储空间")
    @GetMapping("/storage")
    public Result<DriveStorageVO> storage(HttpServletRequest request) {
        return Result.success(cloudDriveService.getStorage(uid(request)));
    }

    @Operation(summary = "扩容云盘存储")
    @PostMapping("/storage/expand")
    public Result<DriveStorageVO> expand(HttpServletRequest request) {
        return Result.success(cloudDriveService.expandStorage(uid(request)));
    }

    @Operation(summary = "列出云盘文件/文件夹")
    @GetMapping("/items")
    public Result<List<DriveItemVO>> listItems(
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.listItems(uid(request), parseNullable(folderId), keyword));
    }

    @Operation(summary = "获取云盘面包屑导航")
    @GetMapping("/breadcrumb")
    public Result<List<DriveItemVO>> breadcrumb(
            @RequestParam(required = false) String folderId,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.breadcrumb(uid(request), parseNullable(folderId)));
    }

    @Operation(summary = "创建云盘文件夹")
    @PostMapping("/folders")
    public Result<DriveItemVO> createFolder(
            @Valid @RequestBody CreateDriveFolderDTO dto,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.createFolder(uid(request), dto));
    }

    @Operation(summary = "上传云盘文件")
    @PostMapping("/files/upload")
    @RateLimit(scope = "cloud:upload", value = 30, window = 60)
    public Result<DriveItemVO> upload(
            @RequestParam(value = "folderId", required = false) String folderId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.upload(uid(request), parseNullable(folderId), file));
    }

    @Operation(summary = "获取云盘文件信息")
    @GetMapping("/files/{fileId}")
    public Result<DriveItemVO> getFile(@PathVariable String fileId, HttpServletRequest request) {
        return Result.success(cloudDriveService.getFile(uid(request), parseId(fileId)));
    }

    /** 鉴权中转下载：所有者才能拉取，不暴露长效签名 URL */
    @Operation(summary = "下载云盘文件内容")
    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> downloadFileContent(
            @PathVariable String fileId,
            HttpServletRequest request) {
        Long userId = uid(request);
        Long id = parseId(fileId);
        DriveItemVO meta = cloudDriveService.getFile(userId, id);
        var object = cloudDriveService.openFileContent(userId, id);
        return MediaStreamResponses.download(object, meta.getName());
    }

    @Operation(summary = "更新云盘文件")
    @PatchMapping("/files/{fileId}")
    public Result<DriveItemVO> updateFile(
            @PathVariable String fileId,
            @Valid @RequestBody UpdateDriveItemDTO dto,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.updateFile(uid(request), parseId(fileId), dto));
    }

    @Operation(summary = "删除云盘文件")
    @DeleteMapping("/files/{fileId}")
    public Result<Void> deleteFile(@PathVariable String fileId, HttpServletRequest request) {
        cloudDriveService.deleteFile(uid(request), parseId(fileId));
        return Result.success(null);
    }

    @Operation(summary = "更新云盘文件夹")
    @PatchMapping("/folders/{folderId}")
    public Result<DriveItemVO> updateFolder(
            @PathVariable String folderId,
            @Valid @RequestBody UpdateDriveItemDTO dto,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.updateFolder(uid(request), parseId(folderId), dto));
    }

    @Operation(summary = "删除云盘文件夹")
    @DeleteMapping("/folders/{folderId}")
    public Result<Void> deleteFolder(@PathVariable String folderId, HttpServletRequest request) {
        cloudDriveService.deleteFolder(uid(request), parseId(folderId));
        return Result.success(null);
    }

    @Operation(summary = "批量删除云盘项目")
    @PostMapping("/items/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody DriveBatchDTO dto, HttpServletRequest request) {
        cloudDriveService.batchDelete(uid(request), dto);
        return Result.success(null);
    }

    @Operation(summary = "批量移动云盘项目")
    @PostMapping("/items/batch-move")
    public Result<Void> batchMove(@Valid @RequestBody DriveBatchDTO dto, HttpServletRequest request) {
        cloudDriveService.batchMove(uid(request), dto);
        return Result.success(null);
    }

    @Operation(summary = "添加文件标签")
    @PostMapping("/files/{fileId}/tags")
    public Result<List<String>> addTag(
            @PathVariable String fileId,
            @Valid @RequestBody DriveTagDTO dto,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.addTag(uid(request), parseId(fileId), dto.getTagName()));
    }

    @Operation(summary = "移除文件标签")
    @DeleteMapping("/files/{fileId}/tags/{tagName}")
    public Result<List<String>> removeTag(
            @PathVariable String fileId,
            @PathVariable String tagName,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.removeTag(uid(request), parseId(fileId), tagName));
    }

    @Operation(summary = "查询云盘操作记录")
    @GetMapping("/activities")
    public Result<List<DriveActivityVO>> activities(
            @RequestParam(required = false) String fileId,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "limit 必须 ≥1") @Max(value = 200, message = "limit 必须 ≤200") int limit,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.listActivities(
                uid(request), parseNullable(fileId), limit));
    }

    @Operation(summary = "创建云盘分享")
    @PostMapping("/shares")
    public Result<DriveShareVO> createShare(
            @Valid @RequestBody CreateDriveShareDTO dto,
            HttpServletRequest request) {
        return Result.success(cloudDriveService.createShare(uid(request), dto));
    }

    @Operation(summary = "撤销云盘分享")
    @DeleteMapping("/shares/{shareId}")
    public Result<Void> revokeShare(@PathVariable String shareId, HttpServletRequest request) {
        cloudDriveService.revokeShare(uid(request), parseId(shareId));
        return Result.success(null);
    }

    /** 公开：查看分享（可免登录） */
    @Operation(summary = "查看公开分享")
    @GetMapping("/share/{token}")
    @RateLimit(scope = "cloud:share:view", value = 60, window = 60, byUser = false)
    public Result<DriveShareVO> publicShare(
            @PathVariable String token,
            @RequestParam(required = false) String password) {
        return Result.success(cloudDriveService.getPublicShare(token, password));
    }

    /** 公开：下载分享文件（短效签名，兼容旧客户端） */
    @Operation(summary = "获取分享下载链接")
    @GetMapping("/share/{token}/download")
    @RateLimit(scope = "cloud:share:download", value = 30, window = 60, byUser = false)
    public Result<Map<String, String>> publicDownload(
            @PathVariable String token,
            @RequestParam(required = false) String password) {
        String url = cloudDriveService.downloadPublicShare(token, password);
        return Result.success(Map.of("url", url));
    }

    /** 公开：中转下载分享文件（推荐，不向前端暴露 MinIO 签名） */
    @Operation(summary = "下载分享文件内容")
    @GetMapping("/share/{token}/content")
    @RateLimit(scope = "cloud:share:content", value = 30, window = 60, byUser = false)
    public ResponseEntity<InputStreamResource> publicDownloadContent(
            @PathVariable String token,
            @RequestParam(required = false) String password) {
        DriveShareVO meta = cloudDriveService.getPublicShare(token, password);
        var object = cloudDriveService.openShareContent(token, password);
        return MediaStreamResponses.download(object, meta.getTargetName());
    }

    private Long uid(HttpServletRequest request) {
        return AuthUtils.requireUserId(request, jwtUtils);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            throw new CustomException(400, "无效的 ID");
        }
    }

    private Long parseNullable(String id) {
        if (!StringUtils.hasText(id)) return null;
        return parseId(id.trim());
    }
}
