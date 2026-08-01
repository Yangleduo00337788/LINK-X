package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "运营活动")
public class AdminActivityVO {

    private Long id;
    private String title;
    /** 展示用地址（同源 /media/activities/{id} 或外链） */
    private String coverUrl;
    /** 入库用对象 key；编辑提交时回传此字段 */
    private String coverKey;
    private String linkUrl;
    private String description;
    private Integer sortOrder;
    private String status;
    private Date startAt;
    private Date endAt;
    private Date publishedAt;
    private Long publishedBy;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date updateTime;
}
