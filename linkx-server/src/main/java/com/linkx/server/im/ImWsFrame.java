package com.linkx.server.im;

import lombok.Data;


@Data
public class ImWsFrame {

    private String action;
    private String clientMsgId;
    private Long serverMsgId;
    private String conversationId;
    private String msgType;
    private String content;
    private String fileName;
    private Long fileSize;
    private String fileUrl;
    /** 语音时长（秒），msgType=voice 时由客户端传入 */
    private Integer voiceDuration;
    private Integer code;
    private String message;
    /**
     * 预留扩展字段，允许承载任意 JSON 结构。
     */
    private Object data;
}
