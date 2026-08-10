package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDriveShareDTO {
    /** file / folder */
    @NotBlank
    @Pattern(regexp = "^(file|folder)$", message = "分享类型仅支持 file 或 folder")
    private String shareType;

    @NotBlank
    private String targetId;

    /** 可选提取码 */
    @Size(max = 64, message = "提取码最多64个字符")
    private String password;

    /** 有效小时数，空=永久 */
    @Min(value = 1, message = "有效时长最少1小时")
    @Max(value = 720, message = "有效时长最多720小时")
    private Integer expireHours;

    /** 最大下载次数，空=不限 */
    @Min(value = 0, message = "最大下载次数不能为负数")
    @Max(value = 100000, message = "最大下载次数不超过100000")
    private Integer maxDownloads;
}
