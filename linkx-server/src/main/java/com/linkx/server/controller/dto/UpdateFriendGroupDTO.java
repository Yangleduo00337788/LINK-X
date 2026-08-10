package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFriendGroupDTO {

    @Size(max = 32, message = "分组名最多32字")
    private String groupName;
}
