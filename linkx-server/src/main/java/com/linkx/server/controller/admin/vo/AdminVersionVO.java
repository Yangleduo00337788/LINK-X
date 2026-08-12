package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "应用版本")
public class AdminVersionVO {

    private Long id;
    private String version;
    private String channel;
    private String platform;
    private String releaseNotes;
    @Schema(description = "入库对象 key 或外链")
    private String downloadKey;
    @Schema(description = "解析后的下载链接（展示/复制）")
    private String downloadUrl;
    private String packageSha256;
    private String packageFileName;
    private Long packageSize;
    private Boolean forceUpdate;
    private String minSupportedVersion;
    private String status;
    private Date publishedAt;
    private Long publishedBy;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date updateTime;
}
