package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkMateTranslateVO {

    private String translatedText;

    /** 实际使用的目标语言描述 */
    private String targetLang;
}
