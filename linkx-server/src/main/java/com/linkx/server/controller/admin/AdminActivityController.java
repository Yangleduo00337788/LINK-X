package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminActivityDTO;
import com.linkx.server.controller.admin.dto.AdminActivityQueryDTO;
import com.linkx.server.controller.admin.vo.AdminActivityUploadVO;
import com.linkx.server.controller.admin.vo.AdminActivityVO;
import com.linkx.server.service.admin.AdminActivityService;
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

@Tag(name = "管理端-活动管理")
@RestController
@RequestMapping("/admin/activities")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminActivityController {

    private final AdminActivityService adminActivityService;

    @Operation(summary = "查询活动列表")
    @GetMapping
    @RequirePermission("admin:activity:list")
    public Result<PageResultVO<AdminActivityVO>> list(@Valid AdminActivityQueryDTO query) {
        return Result.success(adminActivityService.list(query));
    }

    @Operation(summary = "上传活动封面")
    @AuditAction(operationType = "ACTIVITY_UPDATE", description = "上传活动封面")
    @PostMapping("/upload")
    @RequirePermission({"admin:activity:create", "admin:activity:edit"})
    public Result<AdminActivityUploadVO> upload(@RequestParam("file") MultipartFile file,
                                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminActivityService.uploadImage(file, operatorId));
    }

    @Operation(summary = "查询活动详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:activity:view")
    public Result<AdminActivityVO> detail(@PathVariable Long id) {
        return Result.success(adminActivityService.detail(id));
    }

    @Operation(summary = "新增活动")
    @AuditAction(operationType = "ACTIVITY_UPDATE", description = "新增活动")
    @PostMapping
    @RequirePermission("admin:activity:create")
    public Result<AdminActivityVO> create(@Valid @RequestBody AdminActivityDTO dto,
                                          HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminActivityService.create(dto, operatorId));
    }

    @Operation(summary = "编辑活动")
    @AuditAction(operationType = "ACTIVITY_UPDATE", description = "编辑活动")
    @PutMapping("/{id}")
    @RequirePermission("admin:activity:edit")
    public Result<AdminActivityVO> update(@PathVariable Long id,
                                          @Valid @RequestBody AdminActivityDTO dto,
                                          HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminActivityService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除活动")
    @AuditAction(operationType = "ACTIVITY_UPDATE", description = "删除活动")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:activity:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminActivityService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "发布活动")
    @AuditAction(operationType = "ACTIVITY_PUBLISH", description = "发布活动")
    @PostMapping("/{id}/publish")
    @RequirePermission("admin:activity:publish")
    public Result<AdminActivityVO> publish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminActivityService.publish(id, operatorId));
    }

    @Operation(summary = "下线活动")
    @AuditAction(operationType = "ACTIVITY_PUBLISH", description = "下线活动")
    @PostMapping("/{id}/unpublish")
    @RequirePermission("admin:activity:unpublish")
    public Result<AdminActivityVO> unpublish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminActivityService.unpublish(id, operatorId));
    }
}
