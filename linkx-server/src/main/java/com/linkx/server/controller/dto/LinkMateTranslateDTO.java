package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LinkMateTranslateDTO {

    @NotBlank(message = "待翻译内容不能为空")
    @Size(max = 8000, message = "待翻译内容过长")
    private String text;

    /** 目标语言，如 zh / en / ja；留空时由服务端按 auto 处理 */
    @Size(max = 32, message = "目标语言标识过长")
    private String targetLang;
}
