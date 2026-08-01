package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminRecommendDTO;
import com.linkx.server.controller.admin.dto.AdminRecommendQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRecommendUploadVO;
import com.linkx.server.controller.admin.vo.AdminRecommendVO;
import com.linkx.server.service.admin.AdminRecommendService;
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

@Tag(name = "管理端-推荐位管理")
@RestController
@RequestMapping("/admin/recommends")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminRecommendController {

    private final AdminRecommendService adminRecommendService;

    @Operation(summary = "查询推荐位列表")
    @GetMapping
    @RequirePermission("admin:recommend:list")
    public Result<PageResultVO<AdminRecommendVO>> list(@Valid AdminRecommendQueryDTO query) {
        return Result.success(adminRecommendService.list(query));
    }

    @Operation(summary = "上传推荐位图片")
    @AuditAction(operationType = "RECOMMEND_UPDATE", description = "上传推荐位图片")
    @PostMapping("/upload")
    @RequirePermission({"admin:recommend:create", "admin:recommend:edit"})
    public Result<AdminRecommendUploadVO> upload(@RequestParam("file") MultipartFile file,
                                                 HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRecommendService.uploadImage(file, operatorId));
    }

    @Operation(summary = "查询推荐位详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:recommend:view")
    public Result<AdminRecommendVO> detail(@PathVariable Long id) {
        return Result.success(adminRecommendService.detail(id));
    }

    @Operation(summary = "新增推荐位")
    @AuditAction(operationType = "RECOMMEND_UPDATE", description = "新增推荐位")
    @PostMapping
    @RequirePermission("admin:recommend:create")
    public Result<AdminRecommendVO> create(@Valid @RequestBody AdminRecommendDTO dto,
                                           HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRecommendService.create(dto, operatorId));
    }

    @Operation(summary = "编辑推荐位")
    @AuditAction(operationType = "RECOMMEND_UPDATE", description = "编辑推荐位")
    @PutMapping("/{id}")
    @RequirePermission("admin:recommend:edit")
    public Result<AdminRecommendVO> update(@PathVariable Long id,
                                           @Valid @RequestBody AdminRecommendDTO dto,
                                           HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRecommendService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除推荐位")
    @AuditAction(operationType = "RECOMMEND_UPDATE", description = "删除推荐位")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:recommend:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminRecommendService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "发布推荐位")
    @AuditAction(operationType = "RECOMMEND_PUBLISH", description = "发布推荐位")
    @PostMapping("/{id}/publish")
    @RequirePermission("admin:recommend:publish")
    public Result<AdminRecommendVO> publish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRecommendService.publish(id, operatorId));
    }

    @Operation(summary = "下线推荐位")
    @AuditAction(operationType = "RECOMMEND_PUBLISH", description = "下线推荐位")
    @PostMapping("/{id}/unpublish")
    @RequirePermission("admin:recommend:unpublish")
    public Result<AdminRecommendVO> unpublish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRecommendService.unpublish(id, operatorId));
    }
}
