package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class FriendRequestVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toUserId;

    private String fromUsername;

    private String fromNickname;

    private String fromAvatar;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long peerUserId;

    private String peerUsername;

    private String peerNickname;

    private String peerAvatar;

    private String message;

    /** 0=待处理 1=已同意 2=已拒绝 */
    private Integer status;

    /** incoming=我收到的 outgoing=我发出的 */
    private String direction;

    private Date createTime;
}
