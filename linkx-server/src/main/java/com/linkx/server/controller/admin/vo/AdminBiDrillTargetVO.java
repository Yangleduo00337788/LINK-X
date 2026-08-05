package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@Schema(description = "BI 下钻目标")
public class AdminBiDrillTargetVO {

    private String route;
    private Map<String, String> query;
}
