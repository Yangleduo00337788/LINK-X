package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.vo.LocationPlaceVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 位置搜索：代理 OpenStreetMap Nominatim（需遵守其使用政策：合理频次 + User-Agent）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public List<LocationPlaceVO> search(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int cap = Math.min(Math.max(limit, 1), 10);
        String q = keyword.trim();
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit="
                    + cap + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "LinkX/1.0 (local-dev; contact=dev@linkx.local)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Nominatim status={}", response.statusCode());
                throw new CustomException(502, "位置服务暂时不可用");
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<LocationPlaceVO> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String display = text(node, "display_name");
                    String name = display;
                    JsonNode address = node.get("address");
                    if (address != null) {
                        String preferred = firstNonBlank(
                                text(address, "name"),
                                text(address, "amenity"),
                                text(address, "road"),
                                text(address, "suburb"),
                                text(address, "city"),
                                text(address, "town")
                        );
                        if (StringUtils.hasText(preferred)) {
                            name = preferred;
                        }
                    }
                    result.add(LocationPlaceVO.builder()
                            .name(name)
                            .address(display)
                            .lat(node.has("lat") ? node.get("lat").asDouble() : null)
                            .lon(node.has("lon") ? node.get("lon").asDouble() : null)
                            .build());
                }
            }
            return result;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("位置搜索失败: {}", e.getMessage());
            throw new CustomException(502, "位置搜索失败，请稍后重试");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
