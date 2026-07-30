package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "客户端配置更新")
public class ClientSideSettingUpdateDTO {

    @NotNull
    @Schema(description = "客户端登录/注册验证码")
    private Boolean captchaEnabled;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "应用版本号")
    private String appVersion;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "发布渠道")
    private String appChannel;

    @Size(max = 2000)
    @Schema(description = "更新说明")
    private String releaseNotes;

    @Size(max = 512)
    @Schema(description = "下载地址")
    private String downloadUrl;

    @NotNull
    @Min(1024L)
    @Max(2L * 1024 * 1024 * 1024)
    @Schema(description = "最大上传字节数")
    private Long maxUploadBytes;
}
