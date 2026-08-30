package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "应用版本创建/更新")
public class AdminVersionDTO {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "版本号，如 1.0.0")
    private String version;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "发布渠道：stable / beta / dev")
    private String channel;

    @NotBlank
    @Size(max = 16)
    @Schema(description = "目标平台：windows / macos / linux")
    private String platform;

    @Size(max = 16)
    @Schema(description = "安装包格式：exe/msi/dmg/appimage/deb/rpm")
    private String packageFormat;

    @Size(max = 2000)
    @Schema(description = "更新说明")
    private String releaseNotes;

    @Size(max = 512)
    @Schema(description = "下载地址（对象 key 或外链）")
    private String downloadUrl;

    @Size(max = 64)
    @Schema(description = "安装包 SHA-256")
    private String packageSha256;

    @Size(max = 255)
    @Schema(description = "安装包文件名")
    private String packageFileName;

    @Schema(description = "安装包大小（字节）")
    private Long packageSize;

    @NotNull
    @Schema(description = "有可用更新时是否强制升级")
    private Boolean forceUpdate;

    @Size(max = 32)
    @Schema(description = "最低支持版本（低于此版本强制升级；空表示不额外强更）")
    private String minSupportedVersion;
}
