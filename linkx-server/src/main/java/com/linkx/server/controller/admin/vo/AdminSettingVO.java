package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "系统配置视图")
public class AdminSettingVO {

    private Boolean captchaEnabled;
    private String appVersion;
    private String appChannel;
    private String releaseNotes;
    private String downloadUrl;
    private Long maxUploadBytes;
}
