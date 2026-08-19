package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkMateStatusVO {

    private boolean enabled;

    private String model;

    private int dailyTokenLimit;

    private int dailyTokenUsed;

    /** 当前模型是否支持深度思考 */
    private boolean deepThinkingSupported;

    /** 是否已配置 Realtime，可发起灵伴语音通话 */
    private boolean voiceCallSupported;
}
