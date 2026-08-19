package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.dto.LinkMateChatDTO;
import com.linkx.server.controller.dto.LinkMateGroupReplyDTO;
import com.linkx.server.controller.dto.LinkMateTranslateDTO;
import com.linkx.server.controller.vo.LinkMateMessageVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.controller.vo.LinkMateSessionVO;
import com.linkx.server.controller.vo.LinkMateStatusVO;
import com.linkx.server.controller.vo.LinkMateTranslateVO;
import com.linkx.server.controller.vo.LinkMateTranscribeVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface LinkMateService {

    LinkMateStatusVO status(Long userId);

    List<LinkMateSessionVO> listSessions(Long userId);

    LinkMateSessionVO createSession(Long userId);

    void deleteSession(Long userId, Long sessionId);

    LinkMateSessionVO renameSession(Long userId, Long sessionId, String title);

    List<LinkMateMessageVO> listMessages(Long userId, Long sessionId, Long beforeMessageId, int limit);

    LinkMateMessageVO chat(Long userId, LinkMateChatDTO dto);

    SseEmitter streamChat(Long userId, LinkMateChatDTO dto);

    /**
     * 群聊/单聊 @灵伴：生成回复并落入 IM 消息时间线。
     */
    MessageVO replyInImChat(Long userId, LinkMateGroupReplyDTO dto);

    /**
     * 群聊/单聊 @灵伴：SSE 流式回复，完成后落入 IM 消息时间线。
     */
    SseEmitter streamReplyInImChat(Long userId, LinkMateGroupReplyDTO dto);

    /**
     * AI 翻译（复用灵伴 LLM 配置与每日额度）。
     */
    LinkMateTranslateVO translate(Long userId, LinkMateTranslateDTO dto);

    /**
     * AI 语音转文字（上传录音 → Whisper 兼容接口 → 文本）。
     */
    LinkMateTranscribeVO transcribeAudio(Long userId, byte[] audioBytes, String filename, String contentType, String languageHint);
}
