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
    private Integer code;
    private String message;
    private Object data;
}
