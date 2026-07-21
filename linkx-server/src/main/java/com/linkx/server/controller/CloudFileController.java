package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.CloudFileVO;
import com.linkx.server.service.CloudFileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class CloudFileController {

    private final CloudFileService cloudFileService;
    private final JwtUtils jwtUtils;

    @GetMapping
    public Result<List<CloudFileVO>> listMine(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(cloudFileService.listMine(userId, category, limit));
    }
}
