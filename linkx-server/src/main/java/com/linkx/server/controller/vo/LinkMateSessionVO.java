package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkMateSessionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String updateTime;
}
