package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkMateGroupEnabledDTO {

    @NotNull
    private Boolean enabled;
}
