package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.CloudFileVO;
import com.linkx.server.service.CloudFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "${openapi.tag.cloud-file}")
@RequestMapping("/files")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class CloudFileController {

    private final CloudFileService cloudFileService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "查询我的云文件列表")
    @GetMapping
    public Result<List<CloudFileVO>> listMine(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "100") @Min(value = 1, message = "limit 必须 ≥1") @Max(value = 200, message = "limit 必须 ≤200") int limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(cloudFileService.listMine(userId, category, limit));
    }
}
