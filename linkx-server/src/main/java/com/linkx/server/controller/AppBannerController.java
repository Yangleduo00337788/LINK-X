package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.AppBannerVO;
import com.linkx.server.service.admin.AdminBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户端运营 Banner 只读接口。
 */
@Tag(name = "应用-Banner")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppBannerController {

    private final AdminBannerService adminBannerService;

    @Operation(summary = "获取已发布 Banner 列表")
    @GetMapping("/banners")
    public Result<List<AppBannerVO>> list(
            @RequestParam(value = "position", required = false) String position) {
        return Result.success(adminBannerService.listPublishedForClient(position));
    }
}
