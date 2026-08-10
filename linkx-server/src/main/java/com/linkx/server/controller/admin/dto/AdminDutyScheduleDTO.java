package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "值班表")
public class AdminDutyScheduleDTO {

    @NotBlank
    @Schema(description = "名称")
    private String name;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "时区")
    private String timezone;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Valid
    @Schema(description = "值班时段")
    private List<SlotDTO> slots;

    @Data
    @Schema(description = "值班时段")
    public static class SlotDTO {

        @NotNull
        @Min(1)
        @Max(7)
        @Schema(description = "星期 1=周一..7=周日")
        private Integer weekday;

        @NotBlank
        @Schema(description = "开始时间 HH:mm")
        private String startTime;

        @NotBlank
        @Schema(description = "结束时间 HH:mm")
        private String endTime;

        @NotNull
        @Schema(description = "值班处理人")
        private Long assigneeId;

        @Schema(description = "排序")
        private Integer sortOrder;
    }
}
