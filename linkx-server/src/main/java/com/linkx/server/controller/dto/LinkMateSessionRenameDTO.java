package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LinkMateSessionRenameDTO {

    @NotBlank(message = "对话标题不能为空")
    @Size(max = 80, message = "对话标题不能超过 80 字")
    private String title;
}
