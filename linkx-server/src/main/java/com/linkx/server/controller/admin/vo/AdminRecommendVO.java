package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "运营推荐位")
public class AdminRecommendVO {

    private Long id;
    private String slotCode;
    private String title;
    private String subtitle;
    /** 展示用地址（同源 /media/recommends/{id} 或外链） */
    private String imageUrl;
    /** 入库用对象 key；编辑提交时回传此字段 */
    private String imageKey;
    private String linkUrl;
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
