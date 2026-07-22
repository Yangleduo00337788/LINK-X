package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 笔记附件上传结果
 */
@Data
@Builder
public class NoteFileUploadVO {

    /** 预签名 URL（即时预览） */
    private String url;
    /** 对象 key（写入 markdown 的 lx-media: 引用） */
    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
