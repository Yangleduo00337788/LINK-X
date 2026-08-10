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
@Schema(description = "异步导出任务")
public class AdminExportJobVO {

    private Long id;
    private String module;
    private String status;
    private Integer rowCount;
    private String fileName;
    private String errorMessage;
    private Date expireAt;
    private Date createTime;
    private Date updateTime;
}
