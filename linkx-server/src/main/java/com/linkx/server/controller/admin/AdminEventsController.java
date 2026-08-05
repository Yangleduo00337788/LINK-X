package com.linkx.server.controller.admin;

import com.linkx.server.common.RequireRole;
import com.linkx.server.service.admin.AdminEventHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "管理端-实时事件")
@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminEventsController {

    private final AdminEventHub adminEventHub;

    @Operation(summary = "管理端实时事件流（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return adminEventHub.subscribe(userId);
    }
}
