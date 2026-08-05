package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaptchaVO {
    /** image | slider */
    private String type;
    private String captchaId;
    /** 背景图（图形码或滑块背景） */
    private String imageBase64;
    /** 滑块拼图块（仅 slider） */
    private String puzzleImageBase64;
    /** 拼图块纵向偏移（仅 slider） */
    private Integer puzzleY;
    private Long expireSeconds;
}
