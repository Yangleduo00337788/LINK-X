package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminDutyScheduleDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDutyScheduleVO;
import com.linkx.server.service.admin.AdminDutyScheduleService;
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

@Tag(name = "管理端-值班表")
@RestController
@RequestMapping("/admin/duty-schedules")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminDutyScheduleController {

    private final AdminDutyScheduleService adminDutyScheduleService;

    @Operation(summary = "查询值班表列表")
    @GetMapping
    @RequirePermission("admin:duty-schedule:list")
    public Result<PageResultVO<AdminDutyScheduleVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminDutyScheduleService.list(query));
    }

    @Operation(summary = "查询值班表详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:duty-schedule:list")
    public Result<AdminDutyScheduleVO> detail(@PathVariable Long id) {
        return Result.success(adminDutyScheduleService.detail(id));
    }

    @Operation(summary = "新增值班表")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "新增值班表")
    @PostMapping
    @RequirePermission("admin:duty-schedule:create")
    public Result<AdminDutyScheduleVO> create(@Valid @RequestBody AdminDutyScheduleDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminDutyScheduleService.create(dto, operatorId));
    }

    @Operation(summary = "更新值班表")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "更新值班表")
    @PutMapping("/{id}")
    @RequirePermission("admin:duty-schedule:edit")
    public Result<AdminDutyScheduleVO> update(@PathVariable Long id,
                                              @Valid @RequestBody AdminDutyScheduleDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminDutyScheduleService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除值班表")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "删除值班表")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:duty-schedule:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminDutyScheduleService.delete(id, operatorId);
        return Result.success(null);
    }
}
