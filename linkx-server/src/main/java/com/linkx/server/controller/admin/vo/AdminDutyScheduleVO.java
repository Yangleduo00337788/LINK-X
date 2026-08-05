package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "值班表")
public class AdminDutyScheduleVO {

    private Long id;
    private String name;
    private String description;
    private String timezone;
    private Boolean enabled;
    private List<SlotVO> slots;
    private Date createTime;
    private Date updateTime;

    @Data
    @Builder
    @Schema(description = "值班时段")
    public static class SlotVO {

        private Long id;
        private Integer weekday;
        private String startTime;
        private String endTime;
        private Long assigneeId;
        private String assigneeName;
        private Integer sortOrder;
    }
}
