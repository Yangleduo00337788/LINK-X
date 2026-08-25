package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReportShortVideoDTO {

    @NotBlank
    @Size(max = 32)
    private String reason;

    @Size(max = 500)
    private String detail;

    @Size(max = 6)
    private List<@NotBlank @Size(max = 500) String> imageKeys;
}
