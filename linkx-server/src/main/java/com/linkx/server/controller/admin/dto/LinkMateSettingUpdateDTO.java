package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "灵伴（LinkMate）配置更新")
public class LinkMateSettingUpdateDTO {

    @NotNull
    @Schema(description = "是否启用灵伴")
    private Boolean enabled;

    @Schema(description = "API Key；留空表示不修改")
    private String apiKey;

    @NotBlank
    @Schema(description = "API 基址，如 https://api.deepseek.com")
    private String baseUrl;

    @NotBlank
    @Schema(description = "模型名称")
    private String model;

    @NotNull
    @Min(256)
    @Max(32768)
    @Schema(description = "单次最大生成 token")
    private Integer maxTokens;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    @Schema(description = "采样温度")
    private Double temperature;

    @NotNull
    @Min(0)
    @Max(10000000)
    @Schema(description = "单用户每日 token 估算上限，0 表示不限")
    private Integer dailyTokenLimit;

    @Schema(description = "系统提示词；留空使用内置默认")
    private String systemPrompt;
}
