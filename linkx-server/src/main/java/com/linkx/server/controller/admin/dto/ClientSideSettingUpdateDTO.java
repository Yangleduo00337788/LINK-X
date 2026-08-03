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
    @Schema(description = "有可用更新时是否强制升级")
    private Boolean forceUpdate;

    @Size(max = 32)
    @Schema(description = "最低支持版本（低于此版本强制升级；空表示不额外强更）")
    private String minSupportedVersion;

    @NotNull
    @Min(1024L)
    @Max(2L * 1024 * 1024 * 1024)
    @Schema(description = "最大上传字节数")
    private Long maxUploadBytes;

    @NotNull
    @Schema(description = "敏感词过滤总开关")
    private Boolean sensitiveFilterEnabled;

    @Size(max = 128)
    @Schema(description = "客服邮箱")
    private String supportEmail;

    @Size(max = 64)
    @Schema(description = "客服电话")
    private String supportPhone;

    @NotNull
    @Min(1)
    @Max(720)
    @Schema(description = "反馈处理 SLA（小时），1–720")
    private Integer feedbackSlaHours;

    @NotNull
    @Schema(description = "是否启用反馈超时升级")
    private Boolean feedbackEscalationEnabled;

    @NotNull
    @Schema(description = "升级时尝试按分流规则自动改派")
    private Boolean feedbackEscalationAutoReassign;

    @NotNull
    @Min(1)
    @Max(720)
    @Schema(description = "同一工单重复升级间隔（小时），1–720")
    private Integer feedbackEscalationIntervalHours;

    @NotNull
    @Min(1)
    @Max(720)
    @Schema(description = "审核任务 SLA（小时），1–720")
    private Integer reviewSlaHours;

    @NotNull
    @Schema(description = "是否启用审核超时督办")
    private Boolean reviewEscalationEnabled;

    @NotNull
    @Min(1)
    @Max(720)
    @Schema(description = "同一审核任务重复督办间隔（小时），1–720")
    private Integer reviewEscalationIntervalHours;
}
