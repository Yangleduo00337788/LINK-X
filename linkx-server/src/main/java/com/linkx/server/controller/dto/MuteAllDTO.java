package com.linkx.server.controller.dto;

import lombok.Data;

/**
 * 全体禁言 / 定时全体禁言
 */
@Data
public class MuteAllDTO {

    /**
     * 立即开启或关闭全体禁言（与定时字段二选一或组合：开启时可同时带 endTime）
     */
    private Boolean enabled;

    /**
     * 定时开始（毫秒时间戳）；设置后到点自动开启全体禁言
     */
    private Long startTime;

    /**
     * 定时结束（毫秒时间戳）；到点自动关闭全体禁言
     */
    private Long endTime;
}
