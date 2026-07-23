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
@Table("im_message")
public class ImMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_VOICE = "voice";
    /**
     * 红包消息：{@code fileUrl} 存红包 ID，{@code fileName} 存祝福语，{@code fileSize} 存总金额（分）。
     * <p>
     * 该类型仅由 {@code RedPacketServiceImpl.sendRedPacketMessage} 内部生成，
     * 不允许客户端通过 WS 直接以 {@code msgType=redPacket} 上行（仍由 RedPacketController 的 REST 入口发起）。
     * </p>
     */
    public static final String TYPE_RED_PACKET = "redPacket";
    /**
     * 已撤回：原消息原地改为此类型，清空正文/附件，保留时间线位置供客户端渲染系统提示。
     */
    public static final String TYPE_RECALL = "recall";
    /**
     * 系统提示：群管理操作、建群、成为好友等居中灰字，不允许客户端上行。
     */
    public static final String TYPE_SYSTEM = "system";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long conversationId;

    private Long senderId;

    private String type;

    private String content;

    private String fileName;

    private Long fileSize;

    private String fileUrl;

    /**
     * 客户端消息幂等 ID，用于重试去重。
     */
    private String clientMsgId;

    /**
     * 投递状态：pending / delivered / failed。
     */
    private String deliveryStatus;

    /**
     * 已读状态：0 未读 / 1 已读。
     */
    private Integer readStatus;

    /**
     * 语音时长（秒），语音消息专用
     */
    private Integer voiceDuration;

    /** 消息是否被编辑过 */
    @Builder.Default
    private Boolean edited = false;
    /** 最后编辑时间 */
    private Date editedTime;
    /** 转发来源消息 ID */
    private Long forwardFromMessageId;
    /** 转发来源会话 ID */
    private Long forwardFromConversationId;
    /** 引用消息 ID */
    private Long quoteMessageId;
    /** 引用消息所在会话 ID */
    private Long quoteConversationId;
    /** 引用消息发送者 ID */
    private Long quoteSenderId;
    /** 引用消息内容快照 */
    private String quoteContent;
    /** 引用消息类型 */
    private String quoteType;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
