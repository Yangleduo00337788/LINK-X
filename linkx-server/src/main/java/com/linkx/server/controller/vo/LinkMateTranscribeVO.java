package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkMateTranscribeVO {

    private String text;

    /** 识别出的语言（若上游返回） */
    private String language;
}
