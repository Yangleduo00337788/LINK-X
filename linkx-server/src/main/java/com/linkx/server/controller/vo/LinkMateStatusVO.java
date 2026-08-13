package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkMateStatusVO {

    private boolean enabled;

    private String model;

    private int dailyTokenLimit;

    private int dailyTokenUsed;
}
