package com.linkx.server.entity;

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
@Table("conference")
public class Conference implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_ENDED = 2;

    /** 电话（顶栏语音/视频通话） */
    public static final String SCENE_CALL = "call";
    /** 会议（顶栏会议） */
    public static final String SCENE_MEETING = "meeting";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String title;
    /** voice / video */
    private String type;
    /** call / meeting */
    private String scene;
    private Long creatorId;
    private Long conversationId;
    private Integer status;
    private Integer maxParticipants;
    private Date startTime;
    private Date endTime;
    private String password;

    /** 是否开启等候室：1=开 */
    @Builder.Default
    private Integer lobbyEnabled = 0;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
