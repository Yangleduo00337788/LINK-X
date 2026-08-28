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
import jakarta.validation.constraints.Size;
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

    @Schema(description = "语音转写 API Key；留空表示不修改；未配置则回退到灵伴 LLM Key")
    private String sttApiKey;

    @Schema(description = "语音转写 API 基址；留空则回退到灵伴 LLM 基址（DeepSeek 不支持转写）")
    private String sttBaseUrl;

    @Schema(description = "语音转写模型，如 whisper-1")
    private String sttModel;

    @Schema(description = "Realtime API Key；留空表示不修改；未配置则回退到灵伴 LLM Key")
    private String realtimeApiKey;

    @Schema(description = "Realtime API 基址；留空则回退到灵伴 LLM 基址（需 OpenAI Realtime 兼容服务）")
    private String realtimeBaseUrl;

    @Schema(description = "Realtime 模型，如 gpt-realtime")
    private String realtimeModel;

    @Schema(description = "Realtime 输出音色，如 marin")
    private String realtimeVoice;

    @NotNull
    @Schema(description = "是否允许客户端使用 Agent 模式")
    private Boolean agentEnabled;

    @NotNull
    @Schema(description = "新建群是否默认开启灵伴")
    private Boolean groupLinkmateDefaultEnabled;

    @NotNull
    @Schema(description = "新建群是否默认开启群 AI 主动发言")
    private Boolean groupAiProactiveDefaultEnabled;

    @NotNull
    @Schema(description = "新建群是否默认开启群 AI 智能总结")
    private Boolean groupAiSmartSummaryDefaultEnabled;

    @Size(max = 200)
    @Schema(description = "新建群默认关注话题")
    private String groupAiDefaultInterestTopics;

    @Size(max = 500)
    @Schema(description = "新建群默认总结指令")
    private String groupAiDefaultSummaryInstruction;
}
