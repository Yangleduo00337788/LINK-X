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
public class LinkMateVoiceCallStartVO {

    private String callId;

    /** Realtime ephemeral client secret（短时有效，仅本次通话） */
    private String ephemeralKey;

    /** 浏览器 SDP 交换地址（OpenAI /v1/realtime/calls） */
    private String realtimeCallsUrl;

    private String model;

    private String voice;

    private String peerNickname;

    /** ephemeral 过期时间（epoch 秒，可能为 0） */
    private Long expiresAt;

    /** openai | dashscope */
    private String provider;
}
