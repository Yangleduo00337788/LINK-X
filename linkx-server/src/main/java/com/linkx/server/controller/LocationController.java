package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.LocationPlaceVO;
import com.linkx.server.service.LocationService;
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
@Tag(name = "${openapi.tag.location}")
@RequestMapping("/location")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class LocationController {

    private final LocationService locationService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "搜索地点")
    @GetMapping("/search")
    @RateLimit(scope = "location:search", value = 30, window = 60)
    public Result<List<LocationPlaceVO>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") @Min(value = 1, message = "limit 必须 ≥1") @Max(value = 50, message = "limit 必须 ≤50") int limit,
            HttpServletRequest request) {
        AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(locationService.search(q, limit));
    }
}
