package com.linkx.server.controller;

import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.AppActivityVO;
import com.linkx.server.service.admin.AdminActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户端运营活动只读接口。
 */
@Tag(name = "应用-活动")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppActivityController {

    private final AdminActivityService adminActivityService;

    @Operation(summary = "获取已发布活动列表")
    @GetMapping("/activities")
    public Result<List<AppActivityVO>> list() {
        return Result.success(adminActivityService.listPublishedForClient());
    }
}
