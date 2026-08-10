package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "推荐位 创建/更新")
public class AdminRecommendDTO {

    @Schema(description = "推荐位：discover / chat_sidebar / moments")
    @NotBlank
    @Size(max = 64)
    private String slotCode;

    @Schema(description = "标题")
    @Size(max = 128)
    private String title;

    @Schema(description = "副标题")
    @Size(max = 255)
    private String subtitle;

    @Schema(description = "图片对象 key（上传接口返回的 objectKey）")
    @NotBlank
    @Size(max = 1024)
    private String imageUrl;

    @Schema(description = "点击跳转 URL，可空")
    @Size(max = 1024)
    private String linkUrl;

    @Schema(description = "排序，越小越前")
    private Integer sortOrder;

    @Schema(description = "生效开始时间，毫秒时间戳")
    private Long startAt;

    @Schema(description = "生效结束时间，毫秒时间戳")
    private Long endAt;
}
