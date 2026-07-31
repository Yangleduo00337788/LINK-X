package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminBannerDTO;
import com.linkx.server.controller.admin.dto.AdminBannerQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBannerUploadVO;
import com.linkx.server.controller.admin.vo.AdminBannerVO;
import com.linkx.server.service.admin.AdminBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "管理端-Banner管理")
@RestController
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminBannerController {

    private final AdminBannerService adminBannerService;

    @Operation(summary = "查询 Banner 列表")
    @GetMapping
    @RequirePermission("admin:banner:list")
    public Result<PageResultVO<AdminBannerVO>> list(@Valid AdminBannerQueryDTO query) {
        return Result.success(adminBannerService.list(query));
    }

    @Operation(summary = "上传 Banner 图片")
    @PostMapping("/upload")
    @RequirePermission({"admin:banner:create", "admin:banner:edit"})
    public Result<AdminBannerUploadVO> upload(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminBannerService.uploadImage(file, operatorId));
    }

    @Operation(summary = "查询 Banner 详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:banner:view")
    public Result<AdminBannerVO> detail(@PathVariable Long id) {
        return Result.success(adminBannerService.detail(id));
    }

    @Operation(summary = "新增 Banner")
    @AuditAction(operationType = "BANNER_UPDATE", description = "新增Banner")
    @PostMapping
    @RequirePermission("admin:banner:create")
    public Result<AdminBannerVO> create(@Valid @RequestBody AdminBannerDTO dto,
                                        HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminBannerService.create(dto, operatorId));
    }

    @Operation(summary = "编辑 Banner")
    @AuditAction(operationType = "BANNER_UPDATE", description = "编辑Banner")
    @PutMapping("/{id}")
    @RequirePermission("admin:banner:edit")
    public Result<AdminBannerVO> update(@PathVariable Long id,
                                        @Valid @RequestBody AdminBannerDTO dto,
                                        HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminBannerService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除 Banner")
    @AuditAction(operationType = "BANNER_UPDATE", description = "删除Banner")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:banner:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminBannerService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "发布 Banner")
    @AuditAction(operationType = "BANNER_PUBLISH", description = "发布Banner")
    @PostMapping("/{id}/publish")
    @RequirePermission("admin:banner:publish")
    public Result<AdminBannerVO> publish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminBannerService.publish(id, operatorId));
    }

    @Operation(summary = "下线 Banner")
    @AuditAction(operationType = "BANNER_PUBLISH", description = "下线Banner")
    @PostMapping("/{id}/unpublish")
    @RequirePermission("admin:banner:unpublish")
    public Result<AdminBannerVO> unpublish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminBannerService.unpublish(id, operatorId));
    }
}
