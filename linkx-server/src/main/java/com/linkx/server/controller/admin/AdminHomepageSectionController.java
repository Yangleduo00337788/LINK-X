package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminHomepageSectionReorderDTO;
import com.linkx.server.controller.admin.vo.AdminHomepageSectionVO;
import com.linkx.server.service.admin.AdminHomepageSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端-首页编排")
@RestController
@RequestMapping("/admin/homepage-sections")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminHomepageSectionController {

    private final AdminHomepageSectionService adminHomepageSectionService;

    @Operation(summary = "查询首页编排区块")
    @GetMapping
    @RequirePermission("admin:homepage:list")
    public Result<List<AdminHomepageSectionVO>> list() {
        return Result.success(adminHomepageSectionService.listSections());
    }

    @Operation(summary = "批量更新排序与启用状态")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "更新首页编排")
    @PutMapping("/reorder")
    @RequirePermission("admin:homepage:edit")
    public Result<Void> reorder(@Valid @RequestBody AdminHomepageSectionReorderDTO dto,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminHomepageSectionService.reorder(dto, operatorId);
        return Result.success(null);
    }
}
