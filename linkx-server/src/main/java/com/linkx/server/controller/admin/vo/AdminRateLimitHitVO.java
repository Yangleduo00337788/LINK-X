package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "限流 Redis 计数项")
public class AdminRateLimitHitVO {

    private String redisKey;
    private String scope;
    private String ip;
    private String identity;
    private long count;
    private Long ttlSeconds;
}
