package com.linkx.server.controller;

import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.AppRecommendVO;
import com.linkx.server.service.admin.AdminRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户端运营推荐位只读接口。
 */
@Tag(name = "应用-推荐位")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppRecommendController {

    private final AdminRecommendService adminRecommendService;

    @Operation(summary = "获取已发布推荐位列表")
    @GetMapping("/recommends")
    public Result<List<AppRecommendVO>> list(
            @RequestParam(value = "slotCode", required = false) String slotCode) {
        return Result.success(adminRecommendService.listPublishedForClient(slotCode));
    }
}
