package com.linkx.server.entity;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
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
@Table("sys_feedback_reply")
public class SysFeedbackReply implements Serializable {

    public static final String SENDER_ADMIN = "admin";
    public static final String SENDER_USER = "user";

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long feedbackId;
    private String senderType;
    private Long senderId;
    private String senderName;
    private String content;

    @Column(onInsertValue = "NOW()")
    private Date createTime;
}
