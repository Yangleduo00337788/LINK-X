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
public class MessageNotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String type;

    private Long relatedId;

    private String content;

    private Integer readStatus;

    private Date createTime;
}
