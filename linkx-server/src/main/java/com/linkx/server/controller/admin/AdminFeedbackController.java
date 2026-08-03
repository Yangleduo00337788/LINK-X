package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminFeedbackAssignDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackQueryDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackReplyDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackVO;
import com.linkx.server.controller.vo.FeedbackReplyVO;
import com.linkx.server.service.admin.AdminFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-反馈管理")
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    @Operation(summary = "查询反馈列表")
    @GetMapping
    @RequirePermission("admin:feedback:list")
    public Result<PageResultVO<AdminFeedbackVO>> list(@Valid AdminFeedbackQueryDTO query,
                                                      HttpServletRequest request) {
        if (Boolean.TRUE.equals(query.getMineOnly())) {
            query.setAssigneeId((Long) request.getAttribute("userId"));
            query.setUnassignedOnly(false);
        }
        return Result.success(adminFeedbackService.list(query));
    }

    @Operation(summary = "导出反馈 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:feedback:export")
    public ResponseEntity<byte[]> export(@Valid AdminFeedbackQueryDTO query) {
        List<AdminFeedbackVO> items = adminFeedbackService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminFeedbackVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getType()),
                    AdminCsvResponses.cell(item.getContent()),
                    AdminCsvResponses.cell(item.getContact()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getReply()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return AdminCsvResponses.csv("feedback",
                List.of("id", "userId", "username", "type", "content", "contact", "status", "reply", "createTime"),
                rows);
    }

    @Operation(summary = "查询反馈详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:feedback:list")
    public Result<AdminFeedbackVO> detail(@PathVariable Long id) {
        return Result.success(adminFeedbackService.detail(id));
    }

    @Operation(summary = "查询反馈回复记录")
    @GetMapping("/{id}/replies")
    @RequirePermission("admin:feedback:list")
    public Result<List<FeedbackReplyVO>> listReplies(@PathVariable Long id) {
        return Result.success(adminFeedbackService.listReplies(id));
    }

    @Operation(summary = "回复反馈")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "回复反馈")
    @PostMapping("/{id}/reply")
    @RequirePermission("admin:feedback:reply")
    public Result<Void> reply(@PathVariable Long id,
                              @Valid @RequestBody AdminFeedbackReplyDTO dto,
                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminFeedbackService.reply(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "关闭反馈")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "关闭反馈")
    @PostMapping("/{id}/close")
    @RequirePermission("admin:feedback:close")
    public Result<Void> close(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminFeedbackService.close(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "重新打开反馈")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "重新打开反馈")
    @PostMapping("/{id}/reopen")
    @RequirePermission("admin:feedback:reply")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminFeedbackService.reopen(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "指派/改派反馈")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "指派反馈")
    @PutMapping("/{id}/assign")
    @RequirePermission("admin:feedback:assign")
    public Result<Void> assign(@PathVariable Long id,
                               @Valid @RequestBody AdminFeedbackAssignDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminFeedbackService.assign(id, dto, operatorId);
        return Result.success(null);
    }
}
