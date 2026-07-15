package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.dto.SendRedPacketDTO;
import com.linkx.server.controller.vo.RedPacketRecordVO;
import com.linkx.server.controller.vo.RedPacketVO;
import com.linkx.server.entity.*;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.*;
import com.linkx.server.service.BalanceService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.RedPacketService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedPacketServiceImpl implements RedPacketService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RedPacketMapper redPacketMapper;
    private final RedPacketRecordMapper recordMapper;
    private final UserBalanceMapper balanceMapper;
    private final SysUserMapper userMapper;
    private final BalanceService balanceService;
    private final ChatService chatService;

    @Override
    @Transactional
    public RedPacketVO sendRedPacket(Long userId, SendRedPacketDTO dto) {
        chatService.assertConversationMember(userId, dto.getConversationId());

        if (dto.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(400, "红包金额必须大于0");
        }

        if (dto.getTotalCount() > dto.getTotalAmount().divide(new BigDecimal("0.01"), 2, RoundingMode.DOWN).intValue()) {
            throw new CustomException(400, "每个红包金额不能少于0.01元");
        }

        balanceService.deductBalance(userId, dto.getTotalAmount(), "red_packet", null, "发送红包");

        RedPacket redPacket = RedPacket.builder()
                .senderId(userId)
                .conversationId(dto.getConversationId())
                .type(dto.getType() != null ? dto.getType() : RedPacket.TYPE_NORMAL)
                .totalAmount(dto.getTotalAmount())
                .totalCount(dto.getTotalCount())
                .remainingAmount(dto.getTotalAmount())
                .remainingCount(dto.getTotalCount())
                .greeting(dto.getGreeting() != null ? dto.getGreeting() : "恭喜发财")
                .status(RedPacket.STATUS_ACTIVE)
                .expireTime(Date.from(Instant.now().plus(24, ChronoUnit.HOURS)))
                .build();
        redPacketMapper.insert(redPacket);

        sendRedPacketMessage(userId, dto.getConversationId(), redPacket);

        return toRedPacketVO(redPacket, userId);
    }

    @Override
    @Transactional
    public RedPacketVO receiveRedPacket(Long userId, String redPacketIdStr) {
        Long redPacketId = parseId(redPacketIdStr);
        RedPacket redPacket = redPacketMapper.selectOneById(redPacketId);

        if (redPacket == null) {
            throw new CustomException(404, "红包不存在");
        }

        if (redPacket.getStatus().equals(RedPacket.STATUS_FINISHED)) {
            throw new CustomException(400, "红包已领完");
        }

        if (redPacket.getStatus().equals(RedPacket.STATUS_EXPIRED)) {
            throw new CustomException(400, "红包已过期");
        }

        if (redPacket.getExpireTime().before(new Date())) {
            redPacket.setStatus(RedPacket.STATUS_EXPIRED);
            redPacketMapper.update(redPacket);
            throw new CustomException(400, "红包已过期");
        }

        if (redPacket.getSenderId().equals(userId)) {
            throw new CustomException(400, "不能领取自己的红包");
        }

        RedPacketRecord existingRecord = recordMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("red_packet_id", redPacketId)
                        .and("user_id", userId)
        );
        if (existingRecord != null) {
            throw new CustomException(400, "您已领取过该红包");
        }

        BigDecimal receiveAmount;
        if (redPacket.getType().equals(RedPacket.TYPE_LUCKY)) {
            receiveAmount = calculateLuckyAmount(redPacket);
        } else {
            receiveAmount = redPacket.getTotalAmount()
                    .divide(new BigDecimal(redPacket.getTotalCount()), 2, RoundingMode.DOWN);
        }

        redPacket.setRemainingAmount(redPacket.getRemainingAmount().subtract(receiveAmount));
        redPacket.setRemainingCount(redPacket.getRemainingCount() - 1);

        if (redPacket.getRemainingCount() <= 0) {
            redPacket.setStatus(RedPacket.STATUS_FINISHED);
        }

        redPacketMapper.update(redPacket);

        RedPacketRecord record = RedPacketRecord.builder()
                .redPacketId(redPacketId)
                .userId(userId)
                .amount(receiveAmount)
                .isLucky(false)
                .build();
        try {
            recordMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 数据库唯一约束兜底，防止并发请求同时通过应用层检查后重复插入
            throw new CustomException(400, "您已领取过该红包");
        }

        balanceService.addBalance(userId, receiveAmount, "receive_redpacket",
                String.valueOf(redPacketId), "领取红包");

        checkAndMarkLucky(redPacketId, userId, receiveAmount);

        return toRedPacketVO(redPacket, userId);
    }

    @Override
    public RedPacketVO getRedPacket(Long userId, String redPacketIdStr) {
        Long redPacketId = parseId(redPacketIdStr);
        RedPacket redPacket = redPacketMapper.selectOneById(redPacketId);

        if (redPacket == null) {
            throw new CustomException(404, "红包不存在");
        }

        return toRedPacketVO(redPacket, userId);
    }

    @Override
    public List<RedPacketVO> listByConversation(Long userId, Long conversationId) {
        List<RedPacket> redPackets = redPacketMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("conversation_id", conversationId)
                        .orderBy("create_time", false)
                        .limit(50)
        );

        return redPackets.stream()
                .map(rp -> toRedPacketVO(rp, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void expireRedPackets() {
        List<RedPacket> expiredPackets = redPacketMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("status", RedPacket.STATUS_ACTIVE)
                        .and("expire_time", new Date())
        );

        for (RedPacket packet : expiredPackets) {
            if (packet.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                balanceService.unfreezeAndDeduct(packet.getSenderId(),
                        packet.getRemainingAmount(), String.valueOf(packet.getId()));
                balanceService.addBalance(packet.getSenderId(),
                        packet.getRemainingAmount(), "refund",
                        String.valueOf(packet.getId()), "红包过期退款");
            }

            packet.setStatus(RedPacket.STATUS_EXPIRED);
            redPacketMapper.update(packet);
        }
    }

    private BigDecimal calculateLuckyAmount(RedPacket redPacket) {
        if (redPacket.getRemainingCount() == 1) {
            return redPacket.getRemainingAmount();
        }

        double min = 0.01;
        double max = redPacket.getRemainingAmount()
                .divide(new BigDecimal(redPacket.getRemainingCount()), 2, RoundingMode.CEILING)
                .multiply(new BigDecimal(2))
                .doubleValue();

        double random = min + Math.random() * (max - min);
        BigDecimal amount = new BigDecimal(random).setScale(2, RoundingMode.DOWN);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = new BigDecimal("0.01");
        }
        if (amount.compareTo(redPacket.getRemainingAmount()) > 0) {
            amount = redPacket.getRemainingAmount();
        }

        return amount;
    }

    private void checkAndMarkLucky(Long redPacketId, Long userId, BigDecimal amount) {
        List<RedPacketRecord> records = recordMapper.selectListByQuery(
                QueryWrapper.create().eq("red_packet_id", redPacketId)
        );

        if (records.isEmpty()) return;

        RedPacketRecord lucky = records.stream()
                .max(Comparator.comparing(RedPacketRecord::getAmount))
                .orElse(null);

        if (lucky != null && records.size() >= 2) {
            lucky.setIsLucky(true);
            recordMapper.update(lucky);
        }
    }

    private void sendRedPacketMessage(Long senderId, Long conversationId, RedPacket redPacket) {
        SysUser sender = userMapper.selectOneById(senderId);

        String content;
        if (redPacket.getType().equals(RedPacket.TYPE_LUCKY)) {
            content = String.format("[红包] %s 发了一个拼手气红包", sender != null ? sender.getNickname() : "用户");
        } else {
            content = String.format("[红包] %s 发了一个普通红包", sender != null ? sender.getNickname() : "用户");
        }

        SendMessageDTO messageDTO = new SendMessageDTO();
        messageDTO.setConversationId(conversationId);
        messageDTO.setMsgType("redPacket");
        messageDTO.setContent(content);
        messageDTO.setFileUrl(String.valueOf(redPacket.getId()));
        messageDTO.setFileName(redPacket.getGreeting());
        messageDTO.setFileSize(redPacket.getTotalAmount().longValue());

        try {
            chatService.sendMessage(senderId, messageDTO);
        } catch (Exception e) {
            // 消息发送失败不影响红包
        }
    }

    private RedPacketVO toRedPacketVO(RedPacket redPacket, Long currentUserId) {
        SysUser sender = userMapper.selectOneById(redPacket.getSenderId());

        RedPacketRecord userRecord = null;
        if (currentUserId != null) {
            userRecord = recordMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .eq("red_packet_id", redPacket.getId())
                            .and("user_id", currentUserId)
            );
        }

        List<RedPacketRecord> records = recordMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("red_packet_id", redPacket.getId())
                        .orderBy("create_time", true)
        );

        List<Long> userIds = records.stream()
                .map(RedPacketRecord::getUserId)
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectListByQuery(
                    QueryWrapper.create().in("id", userIds)
            ).forEach(u -> userMap.put(u.getId(), u));
        }

        List<RedPacketRecordVO> recordVOs = records.stream()
                .map(r -> {
                    SysUser user = userMap.get(r.getUserId());
                    return RedPacketRecordVO.builder()
                            .id(r.getId())
                            .userId(r.getUserId())
                            .nickname(user != null ? user.getNickname() : null)
                            .avatar(user != null ? user.getAvatar() : null)
                            .amount(r.getAmount())
                            .isLucky(r.getIsLucky())
                            .time(formatTime(r.getCreateTime()))
                            .build();
                })
                .collect(Collectors.toList());

        return RedPacketVO.builder()
                .id(String.valueOf(redPacket.getId()))
                .senderId(redPacket.getSenderId())
                .senderNickname(sender != null ? sender.getNickname() : null)
                .senderAvatar(sender != null ? sender.getAvatar() : null)
                .conversationId(redPacket.getConversationId())
                .type(redPacket.getType())
                .totalAmount(redPacket.getTotalAmount())
                .totalCount(redPacket.getTotalCount())
                .remainingAmount(redPacket.getRemainingAmount())
                .remainingCount(redPacket.getRemainingCount())
                .greeting(redPacket.getGreeting())
                .status(redPacket.getStatus())
                .time(formatTime(redPacket.getCreateTime()))
                .received(userRecord != null)
                .receivedAmount(userRecord != null ? userRecord.getAmount() : null)
                .records(recordVOs)
                .build();
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "无效的ID");
        }
    }

    private String formatTime(Date date) {
        if (date == null) return "";
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_FORMATTER);
    }
}
