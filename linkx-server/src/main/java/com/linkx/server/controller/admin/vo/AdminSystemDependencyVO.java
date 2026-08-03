package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AdminSystemDependencyVO {

    private String name;
    private String status;
    private Long latencyMs;
    private Map<String, Object> details;
}
