package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SendRedPacketDTO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.controller.vo.RedPacketVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.RedPacket;
import com.linkx.server.entity.RedPacketRecord;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.RedPacketMapper;
import com.linkx.server.mapper.RedPacketRecordMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.UserBalanceMapper;
import com.linkx.server.service.BalanceService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedPacketServiceImpl 红包")
class RedPacketServiceImplTest {

    private static final long USER = 10L;
    private static final long PEER = 20L;
    private static final long CONV = 100L;

    @Mock RedPacketMapper redPacketMapper;
    @Mock RedPacketRecordMapper recordMapper;
    @Mock UserBalanceMapper balanceMapper;
    @Mock SysUserMapper userMapper;
    @Mock ImConversationMapper conversationMapper;
    @Mock BalanceService balanceService;
    @Mock ChatService chatService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ImMessagePushService imMessagePushService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock PlatformTransactionManager transactionManager;

    private RedPacketServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        when(chatService.sendMessage(anyLong(), any())).thenReturn(
                MessageVO.builder().id(1L).type("red_packet").build());
        service = new RedPacketServiceImpl(
                redPacketMapper, recordMapper, balanceMapper, userMapper, conversationMapper,
                balanceService, chatService, mediaUrlService, imMessagePushService,
                redisTemplate, transactionManager
        );
    }

    private RedPacket activePacket() {
        return RedPacket.builder()
                .id(5L).senderId(USER).conversationId(CONV).conversationType(ImConversation.TYPE_PRIVATE)
                .type(RedPacket.TYPE_NORMAL).totalAmount(new BigDecimal("1.00")).totalCount(1)
                .remainingAmount(new BigDecimal("1.00")).remainingCount(1)
                .greeting("恭喜发财").status(RedPacket.STATUS_ACTIVE)
                .expireTime(new Date(System.currentTimeMillis() + 3_600_000))
                .version(0L).build();
    }

    @Nested
    @DisplayName("sendRedPacket")
    class Send {
        @Test
        @DisplayName("参数校验")
        void validation() {
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            SendRedPacketDTO dto = new SendRedPacketDTO();
            dto.setConversationId(CONV);
            assertThrows(CustomException.class, () -> service.sendRedPacket(USER, dto));

            dto.setTotalAmount(new BigDecimal("0.001"));
            dto.setTotalCount(1);
            assertThrows(CustomException.class, () -> service.sendRedPacket(USER, dto));

            dto.setTotalAmount(new BigDecimal("0.01"));
            dto.setTotalCount(0);
            assertThrows(CustomException.class, () -> service.sendRedPacket(USER, dto));
        }

        @Test
        @DisplayName("幂等冲突 409")
        void idempotentConflict() {
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(false);
            SendRedPacketDTO dto = new SendRedPacketDTO();
            dto.setConversationId(CONV);
            dto.setTotalAmount(new BigDecimal("1.00"));
            dto.setTotalCount(1);
            dto.setClientMsgId("c1");
            CustomException ex = assertThrows(CustomException.class, () -> service.sendRedPacket(USER, dto));
            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("发送成功")
        void success() {
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true);
            when(conversationMapper.selectOneById(CONV)).thenReturn(
                    ImConversation.builder().id(CONV).type(ImConversation.TYPE_PRIVATE).build());
            when(redPacketMapper.insert(any(RedPacket.class))).thenAnswer(inv -> {
                ((RedPacket) inv.getArgument(0)).setId(5L);
                return 1;
            });
            when(userMapper.selectOneById(USER)).thenReturn(
                    SysUser.builder().id(USER).nickname("Me").avatar("a").build());
            when(recordMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            SendRedPacketDTO dto = new SendRedPacketDTO();
            dto.setConversationId(CONV);
            dto.setTotalAmount(new BigDecimal("1.00"));
            dto.setTotalCount(2);
            dto.setType(RedPacket.TYPE_NORMAL);
            dto.setClientMsgId("c2");
            dto.setGreeting("红包来了");

            RedPacketVO vo = service.sendRedPacket(USER, dto);
            assertEquals("5", vo.getId());
            verify(balanceService).freezeBalance(eq(USER), eq(new BigDecimal("1.00")), anyString());
            verify(chatService).sendMessage(eq(USER), any());
        }
    }

    @Nested
    @DisplayName("get / list / receive")
    class Other {
        @Test
        @DisplayName("getRedPacket")
        void get() {
            when(redPacketMapper.selectOneById(5L)).thenReturn(activePacket());
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(userMapper.selectOneById(USER)).thenReturn(
                    SysUser.builder().id(USER).nickname("Me").build());
            when(recordMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            assertEquals("5", service.getRedPacket(PEER, "5").getId());
        }

        @Test
        @DisplayName("listByConversation")
        void list() {
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(redPacketMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(activePacket()));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUser.builder().id(USER).nickname("Me").build()));
            when(recordMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            assertEquals(1, service.listByConversation(PEER, CONV).size());
        }

        @Test
        @DisplayName("receive 不能领自己的")
        void receiveSelf() {
            when(redPacketMapper.selectOneById(5L)).thenReturn(activePacket());
            when(redPacketMapper.selectByIdForUpdate(5L)).thenReturn(activePacket());
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            CustomException ex = assertThrows(CustomException.class, () -> service.receiveRedPacket(USER, "5"));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("receive 成功")
        void receiveOk() {
            RedPacket packet = activePacket();
            when(redPacketMapper.selectOneById(5L)).thenReturn(packet);
            when(redPacketMapper.selectByIdForUpdate(5L)).thenReturn(packet);
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(recordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(redPacketMapper.updateRemainingAmountAndCount(anyLong(), any(), anyInt(), anyLong())).thenReturn(1);
            RedPacket finished = activePacket();
            finished.setRemainingCount(0);
            finished.setRemainingAmount(BigDecimal.ZERO);
            when(redPacketMapper.selectOneById(5L)).thenReturn(packet, finished);
            when(userMapper.selectOneById(anyLong())).thenReturn(
                    SysUser.builder().id(PEER).nickname("Peer").build());
            when(recordMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            RedPacketVO vo = service.receiveRedPacket(PEER, "5");
            assertNotNull(vo);
            verify(balanceService).unfreezeAndTransfer(eq(USER), eq(PEER), any(), eq("5"));
        }

        @Test
        @DisplayName("receive 已领取")
        void receiveAlready() {
            RedPacket packet = activePacket();
            when(redPacketMapper.selectOneById(5L)).thenReturn(packet);
            when(redPacketMapper.selectByIdForUpdate(5L)).thenReturn(packet);
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(recordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    RedPacketRecord.builder().redPacketId(5L).userId(PEER).amount(new BigDecimal("0.50")).build()
            );
            CustomException ex = assertThrows(CustomException.class, () -> service.receiveRedPacket(PEER, "5"));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("receive 过期红包")
        void receiveExpired() {
            RedPacket packet = activePacket();
            packet.setExpireTime(new Date(System.currentTimeMillis() - 60_000));
            when(redPacketMapper.selectOneById(5L)).thenReturn(packet);
            when(redPacketMapper.selectByIdForUpdate(5L)).thenReturn(packet);
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(recordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.receiveRedPacket(PEER, "5"));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("过期"));
        }

        @Test
        @DisplayName("拼手气红包领取")
        void receiveLuckyDraw() {
            RedPacket packet = RedPacket.builder()
                    .id(5L).senderId(USER).conversationId(CONV).conversationType(ImConversation.TYPE_PRIVATE)
                    .type(RedPacket.TYPE_LUCKY).totalAmount(new BigDecimal("1.00")).totalCount(3)
                    .remainingAmount(new BigDecimal("1.00")).remainingCount(3)
                    .greeting("拼手气").status(RedPacket.STATUS_ACTIVE)
                    .expireTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .version(0L).build();
            when(redPacketMapper.selectOneById(5L)).thenReturn(packet);
            when(redPacketMapper.selectByIdForUpdate(5L)).thenReturn(packet);
            doNothing().when(chatService).assertConversationMember(anyLong(), anyLong());
            when(recordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(redPacketMapper.updateRemainingAmountAndCount(anyLong(), any(), anyInt(), anyLong())).thenReturn(1);
            RedPacket after = RedPacket.builder()
                    .id(5L).senderId(USER).conversationId(CONV)
                    .type(RedPacket.TYPE_LUCKY).totalAmount(new BigDecimal("1.00")).totalCount(3)
                    .remainingAmount(new BigDecimal("0.66")).remainingCount(2)
                    .status(RedPacket.STATUS_ACTIVE).version(1L).build();
            when(redPacketMapper.selectOneById(5L)).thenReturn(packet, after);
            when(userMapper.selectOneById(anyLong())).thenReturn(
                    SysUser.builder().id(PEER).nickname("Peer").build());
            when(recordMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            RedPacketVO vo = service.receiveRedPacket(PEER, "5");
            assertNotNull(vo);
            verify(balanceService).unfreezeAndTransfer(eq(USER), eq(PEER), any(), eq("5"));
        }

        @Test
        @DisplayName("expireRedPackets 批处理退款")
        void expireBatch() {
            when(transactionManager.getTransaction(any())).thenAnswer(inv ->
                    new org.springframework.transaction.support.DefaultTransactionStatus(null, true, false, false, false, null));
            RedPacket expired = activePacket();
            expired.setRemainingAmount(new BigDecimal("0.50"));
            when(redPacketMapper.selectExpiredForUpdate(eq(RedPacket.STATUS_ACTIVE), any(Date.class), anyInt()))
                    .thenReturn(List.of(expired))
                    .thenReturn(List.of());
            when(redPacketMapper.updateStatusWithVersion(eq(5L), eq(0L), eq(RedPacket.STATUS_EXPIRED))).thenReturn(1);

            service.expireRedPackets();
            verify(balanceService).unfreezeAndDeduct(eq(USER), eq(new BigDecimal("0.50")), eq("5"));
        }

        @Test
        @DisplayName("不存在 404")
        void missing() {
            when(redPacketMapper.selectOneById(9L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.getRedPacket(USER, "9"));
        }
    }
}
