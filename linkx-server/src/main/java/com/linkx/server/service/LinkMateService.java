package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.dto.LinkMateChatDTO;
import com.linkx.server.controller.dto.LinkMateGroupReplyDTO;
import com.linkx.server.controller.vo.LinkMateMessageVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.controller.vo.LinkMateSessionVO;
import com.linkx.server.controller.vo.LinkMateStatusVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface LinkMateService {

    LinkMateStatusVO status(Long userId);

    List<LinkMateSessionVO> listSessions(Long userId);

    LinkMateSessionVO createSession(Long userId);

    void deleteSession(Long userId, Long sessionId);

    List<LinkMateMessageVO> listMessages(Long userId, Long sessionId);

    LinkMateMessageVO chat(Long userId, LinkMateChatDTO dto);

    SseEmitter streamChat(Long userId, LinkMateChatDTO dto);

    /**
     * 群聊 @灵伴：生成回复并落入群消息时间线。
     */
    MessageVO replyInGroup(Long userId, LinkMateGroupReplyDTO dto);
}
