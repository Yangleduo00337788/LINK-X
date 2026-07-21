package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.LocationPlaceVO;
import com.linkx.server.service.LocationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final JwtUtils jwtUtils;

    @GetMapping("/search")
    @RateLimit(scope = "location:search", value = 30, window = 60)
    public Result<List<LocationPlaceVO>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit,
            HttpServletRequest request) {
        AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(locationService.search(q, limit));
    }
}
