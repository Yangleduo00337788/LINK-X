package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminNoticeDTO;
import com.linkx.server.controller.admin.dto.AdminNoticeQueryDTO;
import com.linkx.server.controller.admin.vo.AdminNoticeVO;
import com.linkx.server.service.admin.AdminNoticeService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-公告管理")
@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    @Operation(summary = "查询公告列表")
    @GetMapping
    @RequirePermission("admin:notice:list")
    public Result<PageResultVO<AdminNoticeVO>> list(@Valid AdminNoticeQueryDTO query) {
        return Result.success(adminNoticeService.list(query));
    }

    @Operation(summary = "管理端通知收件箱（已发布的管理端公告）")
    @GetMapping("/inbox")
    @RequirePermission("admin:notice:inbox")
    public Result<PageResultVO<AdminNoticeVO>> inbox(@Valid AdminNoticeQueryDTO query) {
        return Result.success(adminNoticeService.listInbox(query));
    }

    @Operation(summary = "查询公告详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:notice:view")
    public Result<AdminNoticeVO> detail(@PathVariable Long id) {
        return Result.success(adminNoticeService.detail(id));
    }

    @Operation(summary = "新增公告")
    @AuditAction(operationType = "NOTICE_UPDATE", description = "新增公告")
    @PostMapping
    @RequirePermission("admin:notice:create")
    public Result<AdminNoticeVO> create(@Valid @RequestBody AdminNoticeDTO dto,
                                        HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminNoticeService.create(dto, operatorId));
    }

    @Operation(summary = "编辑公告")
    @AuditAction(operationType = "NOTICE_UPDATE", description = "编辑公告")
    @PutMapping("/{id}")
    @RequirePermission("admin:notice:edit")
    public Result<AdminNoticeVO> update(@PathVariable Long id,
                                        @Valid @RequestBody AdminNoticeDTO dto,
                                        HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminNoticeService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除公告")
    @AuditAction(operationType = "NOTICE_UPDATE", description = "删除公告")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:notice:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminNoticeService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "发布公告")
    @AuditAction(operationType = "NOTICE_PUBLISH", description = "发布公告")
    @PostMapping("/{id}/publish")
    @RequirePermission("admin:notice:publish")
    public Result<AdminNoticeVO> publish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminNoticeService.publish(id, operatorId));
    }

    @Operation(summary = "下线公告")
    @AuditAction(operationType = "NOTICE_PUBLISH", description = "下线公告")
    @PostMapping("/{id}/unpublish")
    @RequirePermission("admin:notice:unpublish")
    public Result<AdminNoticeVO> unpublish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminNoticeService.unpublish(id, operatorId));
    }
}
