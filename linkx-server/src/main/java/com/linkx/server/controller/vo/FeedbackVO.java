package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private String content;
    private String status;
    private String reply;
    private Date replyTime;
    private Date createTime;
    private List<FeedbackReplyVO> replies;
}
