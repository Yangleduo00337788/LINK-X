package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveCalendarEventDTO {

    @NotBlank(message = "日程标题不能为空")
    @Size(max = 200, message = "标题最多200字")
    private String title;

    @NotBlank(message = "日期不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式错误，应为 YYYY-MM-DD")
    private String date;

    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "时间格式错误，应为 HH:mm")
    private String time;

    @Size(max = 50, message = "颜色值过长")
    private String color;
}
