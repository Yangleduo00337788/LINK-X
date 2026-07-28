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
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.RedPacketService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    /** 红包发送幂等键前缀，TTL 略大于红包过期时间(24h) */
    private static final String IDEM_KEY_PREFIX = "linkx:redpacket:idem:";
    private static final Duration IDEM_KEY_TTL = Duration.ofHours(25);

    private final RedPacketMapper redPacketMapper;
    private final RedPacketRecordMapper recordMapper;
    private final UserBalanceMapper balanceMapper;
    private final SysUserMapper userMapper;
    private final BalanceService balanceService;
    private final ChatService chatService;
    private final MediaUrlService mediaUrlService;
    private final StringRedisTemplate redisTemplate;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public RedPacketVO sendRedPacket(Long userId, SendRedPacketDTO dto) {
        chatService.assertConversationMember(userId, dto.getConversationId());

        if (dto.getTotalAmount() == null || dto.getTotalCount() == null) {
            throw new CustomException(400, "红包金额与个数不能为空");
        }
        // 统一到分，拒绝超精度绕过
        BigDecimal amount = dto.getTotalAmount().setScale(2, RoundingMode.DOWN);
        if (amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new CustomException(400, "红包金额必须大于0");
        }
        if (dto.getTotalCount() < 1) {
            throw new CustomException(400, "红包个数最少为1");
        }
        // 用 compareTo 替代 intValue()，避免超大金额 int 溢出绕过校验
        BigDecimal maxCount = amount.divide(new BigDecimal("0.01"), 2, RoundingMode.DOWN);
        if (new BigDecimal(dto.getTotalCount()).compareTo(maxCount) > 0) {
            throw new CustomException(400, "每个红包金额不能少于0.01元");
        }
        String type = dto.getType() != null ? dto.getType() : RedPacket.TYPE_NORMAL;
        if (!RedPacket.TYPE_NORMAL.equals(type) && !RedPacket.TYPE_LUCKY.equals(type)) {
            throw new CustomException(400, "红包类型仅支持 normal 或 lucky");
        }
        dto.setType(type);
        dto.setTotalAmount(amount);

        // 幂等去重：同一用户同一 clientMsgId 仅生效一次，防止网络重试/双击重复扣款
        String idemKey = IDEM_KEY_PREFIX + userId + ":" + dto.getClientMsgId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(idemKey, "1", IDEM_KEY_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            throw new CustomException(409, "红包正在发送中，请勿重复提交");
        }
        // 事务回滚时清理幂等键，避免 IDEM_KEY_TTL 窗口内用户无法合法重试（资金侧故障）
        registerIdempotencyKeyRollbackCleanup(idemKey);

        // 冻结红包金额（原子 SQL：balance >= amount，余额不足则失败）
        balanceService.freezeBalance(userId, amount, "redpacket:" + dto.getClientMsgId());

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
                .clientMsgId(dto.getClientMsgId())
                .version(0L)
                .build();
        redPacketMapper.insert(redPacket);

        sendRedPacketMessage(userId, dto.getConversationId(), redPacket);

        return toRedPacketVO(redPacket, userId);
    }

    @Override
    @Transactional
    public RedPacketVO receiveRedPacket(Long userId, String redPacketIdStr) {
        Long redPacketId = parseId(redPacketIdStr);

        // 先用非锁读检查过期：过期则独立事务标记后拒绝，
        // 避免 FOR UPDATE 行锁内调 REQUIRES_NEW 更新同一行导致死锁
        RedPacket snapshot = redPacketMapper.selectOneById(redPacketId);
        if (snapshot == null) {
            throw new CustomException(404, "红包不存在");
        }
        if (RedPacket.STATUS_EXPIRED.equals(snapshot.getStatus())) {
            throw new CustomException(400, "红包已过期");
        }
        if (RedPacket.STATUS_FINISHED.equals(snapshot.getStatus())) {
            throw new CustomException(400, "红包已领完");
        }
        if (snapshot.getExpireTime() != null && snapshot.getExpireTime().before(new Date())) {
            markRedPacketExpiredInNewTx(redPacketId);
            throw new CustomException(400, "红包已过期");
        }

        RedPacket redPacket = redPacketMapper.selectByIdForUpdate(redPacketId);

            if (redPacket == null) {
                throw new CustomException(404, "红包不存在");
            }

            // 非会话成员不可领取（防猜 ID 盗领）
            chatService.assertConversationMember(userId, redPacket.getConversationId());

            if (redPacket.getStatus().equals(RedPacket.STATUS_FINISHED)) {
                throw new CustomException(400, "红包已领完");
            }

            if (redPacket.getStatus().equals(RedPacket.STATUS_EXPIRED)) {
                throw new CustomException(400, "红包已过期");
            }

            if (redPacket.getExpireTime().before(new Date())) {
                // 并发窗口内过期：仅拒绝，标记交由批处理任务 expireRedPackets
                throw new CustomException(400, "红包已过期");
            }

            if (redPacket.getSenderId().equals(userId)) {
                throw new CustomException(400, "不能领取自己的红包");
            }

            RedPacketRecord existingRecord = recordMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .eq("red_packet_id", redPacketId)
                            .eq("user_id", userId)
            );
            if (existingRecord != null) {
                throw new CustomException(400, "您已领取过该红包");
            }

            // 计算领取金额：最后一个红包拿剩余，避免普通包除不尽尾差沉没
            BigDecimal receiveAmount;
            if (redPacket.getRemainingCount() != null && redPacket.getRemainingCount() == 1) {
                receiveAmount = redPacket.getRemainingAmount();
            } else if (redPacket.getType().equals(RedPacket.TYPE_LUCKY)) {
                receiveAmount = calculateLuckyAmount(redPacket);
            } else {
                receiveAmount = redPacket.getTotalAmount()
                        .divide(new BigDecimal(redPacket.getTotalCount()), 2, RoundingMode.DOWN);
            }

            // 更新红包剩余金额和个数（乐观锁保护）
            int updatedRows = redPacketMapper.updateRemainingAmountAndCount(
                    redPacketId,
                    receiveAmount,
                    redPacket.getRemainingCount() - 1,
                    redPacket.getVersion()
            );

            if (updatedRows == 0) {
                throw new CustomException(409, "红包状态已变化，请重试");
            }

            // 更新红包状态
            RedPacket updatedPacket = redPacketMapper.selectOneById(redPacketId);
            if (updatedPacket == null) {
                throw new CustomException(404, "红包不存在");
            }
            if (updatedPacket.getRemainingCount() <= 0) {
                updatedPacket.setStatus(RedPacket.STATUS_FINISHED);
                redPacketMapper.update(updatedPacket);
            }

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

            // 从发送者冻结金额转给领取者
            balanceService.unfreezeAndTransfer(redPacket.getSenderId(), userId, receiveAmount,
                    String.valueOf(redPacketId));

            checkAndMarkLucky(redPacketId, userId, receiveAmount);

            return toRedPacketVO(updatedPacket, userId);
    }

    @Override
    public RedPacketVO getRedPacket(Long userId, String redPacketIdStr) {
        Long redPacketId = parseId(redPacketIdStr);
        RedPacket redPacket = redPacketMapper.selectOneById(redPacketId);

        if (redPacket == null) {
            throw new CustomException(404, "红包不存在");
        }
        chatService.assertConversationMember(userId, redPacket.getConversationId());

        return toRedPacketVO(redPacket, userId);
    }

    @Override
    public List<RedPacketVO> listByConversation(Long userId, Long conversationId) {
        chatService.assertConversationMember(userId, conversationId);
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
        // 使用 FOR UPDATE 行锁，防止 TOCTOU 超退（并发领包时按快照多退）
        List<RedPacket> expiredPackets = redPacketMapper.selectExpiredForUpdate(
                RedPacket.STATUS_ACTIVE, new Date());

        for (RedPacket packet : expiredPackets) {
            // 用 DB 当前值退款，而非快照值
            BigDecimal refundAmount = packet.getRemainingAmount();

            // 先用乐观锁将状态更新为 EXPIRED（防止重复处理）
            int updated = redPacketMapper.updateStatusWithVersion(
                    packet.getId(), packet.getVersion(), RedPacket.STATUS_EXPIRED);

            if (updated == 0) {
                // 乐观锁冲突，说明红包在其他事务中被处理，跳过
                continue;
            }

            // 退款（仅当有剩余金额时）
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                balanceService.unfreezeAndDeduct(packet.getSenderId(), refundAmount,
                        String.valueOf(packet.getId()));
            }
        }
    }

    private BigDecimal calculateLuckyAmount(RedPacket redPacket) {
        if (redPacket.getRemainingCount() == 1) {
            return redPacket.getRemainingAmount();
        }

        // 使用纯 BigDecimal 运算，避免 double 精度丢失
        BigDecimal remaining = redPacket.getRemainingAmount();
        int remainingCount = redPacket.getRemainingCount();

        // max = remaining / remainingCount * 2
        BigDecimal maxAmount = remaining
                .divide(BigDecimal.valueOf(remainingCount), 10, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(2));

        // 随机区间分成 10000 份，避免浮点数
        BigDecimal range = maxAmount.subtract(BigDecimal.valueOf(0.01));
        if (range.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP);
        }
        long rangeUnits = range.multiply(BigDecimal.valueOf(100)).longValue();
        // [P3] 使用 ThreadLocalRandom 替代 Math.random()，避免全局锁竞争
        long randomUnits = java.util.concurrent.ThreadLocalRandom.current().nextLong(rangeUnits + 1);
        BigDecimal amount = BigDecimal.valueOf(0.01)
                .add(BigDecimal.valueOf(randomUnits).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN));

        if (amount.compareTo(BigDecimal.valueOf(0.01)) < 0) {
            amount = BigDecimal.valueOf(0.01);
        }
        if (amount.compareTo(remaining) > 0) {
            amount = remaining;
        }

        return amount.setScale(2, RoundingMode.DOWN);
    }

    /**
     * 用独立事务（REQUIRES_NEW）标记红包过期，确保外层事务回滚后过期状态仍持久化。
     * 必须在 FOR UPDATE 行锁获取前调用，否则会因行锁等待导致死锁。
     */
    private void markRedPacketExpiredInNewTx(Long redPacketId) {
        org.springframework.transaction.support.TransactionTemplate tt =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> {
            RedPacket rp = redPacketMapper.selectOneById(redPacketId);
            if (rp != null && RedPacket.STATUS_ACTIVE.equals(rp.getStatus())) {
                rp.setStatus(RedPacket.STATUS_EXPIRED);
                redPacketMapper.update(rp);
            }
        });
    }

    private void checkAndMarkLucky(Long redPacketId, Long userId, BigDecimal amount) {
        // 只在红包被领完时（STATUS_FINISHED）才决定手气最佳，
        // 此时乐观锁已保证没有其他并发线程能再领取，避免多线程各自标记不同记录
        RedPacket redPacket = redPacketMapper.selectOneById(redPacketId);
        if (redPacket == null || !RedPacket.STATUS_FINISHED.equals(redPacket.getStatus())) {
            return;
        }
        // 仅拼手气红包标记手气最佳，普通红包无此概念
        if (!RedPacket.TYPE_LUCKY.equals(redPacket.getType())) {
            return;
        }
        // 直接用数据库排序查询当前最高金额的记录；
        // 并列时按领取时间最早者优先（id 升序），保证"手气最佳"结果确定可复现
        RedPacketRecord currentMax = recordMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("red_packet_id", redPacketId)
                        .orderBy("amount", false)
                        .orderBy("id", true)
                        .limit(1)
        );
        if (currentMax == null) return;
        long count = recordMapper.selectCountByQuery(
                QueryWrapper.create().eq("red_packet_id", redPacketId)
        );
        if (count >= 2) {
            currentMax.setIsLucky(true);
            recordMapper.update(currentMax);
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
        // SendMessageDTO.fileSize 是 Long（字节数），红包总金额用「分」存，避免 BigDecimal 序列化
        messageDTO.setFileSize(redPacket.getTotalAmount().multiply(new BigDecimal("100")).longValue());

        // 与发红包同一事务：消息失败则整笔回滚，避免有包无气泡
        chatService.sendMessage(senderId, messageDTO);
    }

    private RedPacketVO toRedPacketVO(RedPacket redPacket, Long currentUserId) {
        SysUser sender = userMapper.selectOneById(redPacket.getSenderId());

        RedPacketRecord userRecord = null;
        if (currentUserId != null) {
            userRecord = recordMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .eq("red_packet_id", redPacket.getId())
                            .eq("user_id", currentUserId)
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
                            .avatar(user != null ? mediaUrlService.resolve(user.getAvatar()) : null)
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
                .senderAvatar(sender != null ? mediaUrlService.resolve(sender.getAvatar()) : null)
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
        if (id == null || id.isEmpty()) {
            throw new CustomException(400, "无效的ID");
        }
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

    /**
     * 注册事务回滚时的幂等键清理钩子：
     * 当前事务成功提交时幂等键保留（防止 TTL 窗口内重复发送）；
     * 当前事务回滚时清理幂等键（防止余额已冻结/未冻结的中间态下用户被锁死无法重试）。
     */
    private void registerIdempotencyKeyRollbackCleanup(String idemKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        redisTemplate.delete(idemKey);
                    } catch (Exception e) {
                        // 不抛异常外溢到事务框架；清理失败仅依赖 TTL 自动过期兜底
                        org.slf4j.LoggerFactory.getLogger(RedPacketServiceImpl.class)
                                .warn("清理红包幂等键失败: key={}, err={}", idemKey, e.getMessage());
                    }
                }
            }
        });
    }
}
