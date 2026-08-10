package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.AppHomepageVO;
import com.linkx.server.service.admin.AdminHomepageSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "应用-首页编排")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppHomepageController {

    private final AdminHomepageSectionService adminHomepageSectionService;

    @Operation(summary = "获取客户端首页编排与内容")
    @GetMapping("/homepage")
    public Result<AppHomepageVO> homepage() {
        return Result.success(adminHomepageSectionService.buildClientHomepage());
    }
}
