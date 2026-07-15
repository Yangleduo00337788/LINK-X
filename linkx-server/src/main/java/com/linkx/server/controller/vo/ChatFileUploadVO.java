package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatFileUploadVO {

    /** 签名 URL（前端展示用，1 小时后过期） */
    private String url;
    /** 对象 key（用于后续调用删除或刷新签名 URL） */
    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
