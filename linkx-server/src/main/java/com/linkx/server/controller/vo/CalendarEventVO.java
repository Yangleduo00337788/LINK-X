package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String date;

    private String time;

    private String color;

    private Date createTime;

    private Date updateTime;
}
