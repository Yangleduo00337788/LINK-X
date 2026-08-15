package com.linkx.server.service.customerservice;

import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.common.RbacConstants;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.RbacService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import org.springframework.dao.DuplicateKeyException;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * LinkX 客服：确保机器人账号、好友关系、私聊会话，并处理用户消息自动回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceService implements ApplicationRunner {

    private static final int RELATION_STATUS_NORMAL = 1;

    private final LinkxProperties linkxProperties;
    private final SysUserMapper sysUserMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageRepository imMessageRepository;
    private final FeedbackMapper feedbackMapper;
    private final ChatService chatService;
    private final ImMessagePushService imMessagePushService;
    private final CustomerServiceBotResponder botResponder;
    private final RbacService rbacService;
    @Qualifier("imPushExecutor")
    private final Executor imPushExecutor;

    @Override
    public void run(ApplicationArguments args) {
        if (!isEnabled()) {
            return;
        }
        try {
            Long botId = ensureBotUser();
            CustomerServiceRegistry.setBotUserId(botId);
            log.info("LinkX客服机器人已就绪，userId={}", botId);
        } catch (Exception e) {
            log.error("LinkX客服机器人初始化失败", e);
        }
    }

    public boolean isEnabled() {
        return linkxProperties.getCustomerService().isEnabled();
    }

    public Long botUserId() {
        return CustomerServiceRegistry.botUserId();
    }

    @Transactional
    public ConversationVO ensureSession(Long userId) {
        if (!isEnabled()) {
            throw new CustomException(503, "客服功能暂未开启");
        }
        Long botId = botUserId();
        if (botId == null) {
            botId = ensureBotUser();
            CustomerServiceRegistry.setBotUserId(botId);
        }
        ensureBidirectionalRelation(userId, botId);
        ConversationVO conversation = chatService.getOrCreatePrivateConversation(userId, botId);
        if (linkxProperties.getCustomerService().isAutoPin()) {
            pinConversationForUser(userId, conversation.getId());
        }
        scheduleWelcomeAfterCommit(userId, botId, conversation.getId());
        return conversation;
    }

    public void onUserTextMessageAsync(Long senderId, MessageVO userMessage) {
        if (!isEnabled() || senderId == null || userMessage == null) {
            return;
        }
        if (!ImMessage.TYPE_TEXT.equals(userMessage.getType())) {
            return;
        }
        Long botId = botUserId();
        if (botId == null || CustomerServiceRegistry.isBot(senderId)) {
            return;
        }
        Long conversationId = userMessage.getConversationId();
        if (conversationId == null || !isCustomerServiceConversation(senderId, conversationId)) {
            return;
        }
        String content = userMessage.getContent();
        imPushExecutor.execute(() -> {
            try {
                String reply = botResponder.reply(content, listUserFeedbacks(senderId));
                MessageVO botMessage = chatService.postCustomerServiceMessage(conversationId, reply);
                imMessagePushService.pushToConversationMembers(botMessage, botId, null);
            } catch (Exception e) {
                log.warn("客服自动回复失败 userId={} conversationId={}: {}", senderId, conversationId, e.toString());
            }
        });
    }

    public boolean isCustomerServiceConversation(Long userId, Long conversationId) {
        if (userId == null || conversationId == null || !isEnabled()) {
            return false;
        }
        Long botId = botUserId();
        if (botId == null) {
            return false;
        }
        ImConversation conversation = chatService.findConversationById(conversationId);
        if (conversation == null || conversation.getType() != ImConversation.TYPE_PRIVATE) {
            return false;
        }
        Long peerId = resolvePrivatePeerId(userId, conversationId);
        return CustomerServiceRegistry.isBot(peerId);
    }

    private List<Feedback> listUserFeedbacks(Long userId) {
        return feedbackMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Feedback::getUserId).eq(userId)
                        .orderBy(Feedback::getCreateTime, false)
                        .limit(20)
        );
    }

    private void scheduleWelcomeAfterCommit(Long userId, Long botId, Long conversationId) {
        Runnable task = () -> {
            try {
                maybeSendWelcome(userId, botId, conversationId);
            } catch (Exception e) {
                log.warn("客服欢迎语发送失败 userId={} conversationId={}: {}", userId, conversationId, e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void maybeSendWelcome(Long userId, Long botId, Long conversationId) {
        long count = imMessageRepository.selectCountByQuery(
                QueryWrapper.create().where(ImMessage::getConversationId).eq(conversationId)
        );
        if (count > 0) {
            return;
        }
        MessageVO welcome = chatService.postCustomerServiceMessage(conversationId, botResponder.welcomeMessage());
        imMessagePushService.pushToConversationMembers(welcome, botId, null);
    }

    private void pinConversationForUser(Long userId, Long conversationId) {
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
                        .limit(1)
        );
        if (member != null && (member.getPinned() == null || member.getPinned() == 0)) {
            member.setPinned(1);
            memberMapper.update(member);
        }
    }

    private Long ensureBotUser() {
        String username = configuredBotUsername();
        SysUser existing = findBotUserIncludingDeleted(username);
        if (existing != null) {
            reviveBotUserIfNeeded(existing, configuredBotNickname());
            return existing.getId();
        }
        Date now = new Date();
        SysUser bot = SysUser.builder()
                .username(username)
                .password(PasswordEncoderHolder.encode("CsBot!" + UUID.randomUUID()))
                .nickname(configuredBotNickname())
                .status(1)
                .deviceBindingEnabled(0)
                .totpEnabled(0)
                .deleted(0)
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            sysUserMapper.insert(bot);
        } catch (DuplicateKeyException e) {
            SysUser raced = findBotUserIncludingDeleted(username);
            if (raced != null) {
                reviveBotUserIfNeeded(raced, configuredBotNickname());
                return raced.getId();
            }
            throw e;
        }
        try {
            rbacService.grantRole(bot.getId(), RbacConstants.ROLE_USER, null);
        } catch (Exception e) {
            log.warn("客服机器人分配默认角色失败 userId={}: {}", bot.getId(), e.toString());
        }
        return bot.getId();
    }

    private String configuredBotUsername() {
        String username = linkxProperties.getCustomerService().getUsername();
        return StringUtils.hasText(username) ? username : CustomerServiceConstants.BOT_USERNAME;
    }

    private String configuredBotNickname() {
        String nickname = linkxProperties.getCustomerService().getNickname();
        return StringUtils.hasText(nickname) ? nickname : "LinkX客服";
    }

    private SysUser findBotUserIncludingDeleted(String username) {
        return LogicDeleteManager.execWithoutLogicDelete(() ->
                sysUserMapper.selectOneByQuery(
                        QueryWrapper.create().where(SysUser::getUsername).eq(username).limit(1)
                )
        );
    }

    private void reviveBotUserIfNeeded(SysUser existing, String nickname) {
        boolean dirty = false;
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            existing.setStatus(1);
            dirty = true;
        }
        if (existing.getDeleted() != null && existing.getDeleted() != 0) {
            existing.setDeleted(0);
            dirty = true;
        }
        if (!StringUtils.hasText(existing.getNickname()) && StringUtils.hasText(nickname)) {
            existing.setNickname(nickname);
            dirty = true;
        }
        if (!dirty) {
            return;
        }
        existing.setUpdateTime(new Date());
        LogicDeleteManager.execWithoutLogicDelete(() -> {
            sysUserMapper.update(existing);
            return null;
        });
    }

    private void ensureBidirectionalRelation(Long userA, Long userB) {
        ensureRelation(userA, userB);
        ensureRelation(userB, userA);
    }

    private void ensureRelation(Long userId, Long friendId) {
        if (isFriend(userId, friendId)) {
            return;
        }
        Date now = new Date();
        SysUserRelation existing = LogicDeleteManager.execWithoutLogicDelete(() ->
                sysUserRelationMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(SysUserRelation::getUserId).eq(userId)
                                .and(SysUserRelation::getFriendId).eq(friendId)
                                .limit(1)
                )
        );
        if (existing != null) {
            existing.setStatus(RELATION_STATUS_NORMAL);
            existing.setDeleted(0);
            existing.setUpdateTime(now);
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                sysUserRelationMapper.update(existing);
                return null;
            });
            return;
        }
        try {
            sysUserRelationMapper.insert(SysUserRelation.builder()
                    .userId(userId)
                    .friendId(friendId)
                    .status(RELATION_STATUS_NORMAL)
                    .deleted(0)
                    .createTime(now)
                    .updateTime(now)
                    .build());
        } catch (DuplicateKeyException e) {
            SysUserRelation raced = LogicDeleteManager.execWithoutLogicDelete(() ->
                    sysUserRelationMapper.selectOneByQuery(
                            QueryWrapper.create()
                                    .where(SysUserRelation::getUserId).eq(userId)
                                    .and(SysUserRelation::getFriendId).eq(friendId)
                                    .limit(1)
                    )
            );
            if (raced == null) {
                throw e;
            }
            raced.setStatus(RELATION_STATUS_NORMAL);
            raced.setDeleted(0);
            raced.setUpdateTime(now);
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                sysUserRelationMapper.update(raced);
                return null;
            });
        }
    }

    private boolean isFriend(Long userId, Long friendId) {
        return sysUserRelationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(friendId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_NORMAL)
        ) > 0;
    }

    private Long resolvePrivatePeerId(Long userId, Long conversationId) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        return members.stream()
                .map(ImConversationMember::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElse(null);
    }
}
