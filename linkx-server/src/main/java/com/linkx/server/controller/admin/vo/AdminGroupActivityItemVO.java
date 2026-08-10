package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "群活跃榜单项")
public class AdminGroupActivityItemVO {

    private Long id;
    private String name;
    private Long messageCount;
    private Long memberCount;
    private Date lastMessageTime;
}
