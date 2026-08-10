package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupRemarkDTO {

    @Size(max = 64, message = "群备注最多64字")
    private String remark;
}
