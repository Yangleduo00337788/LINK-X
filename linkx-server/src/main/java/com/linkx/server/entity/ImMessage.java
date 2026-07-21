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
     * 语音时长（秒），语音消息专用
     */
    private Integer voiceDuration;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
