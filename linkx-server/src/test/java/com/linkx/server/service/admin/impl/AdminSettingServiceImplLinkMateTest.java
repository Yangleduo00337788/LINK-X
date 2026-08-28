package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.MailSenderHolder;
import com.linkx.server.controller.admin.dto.LinkMateSettingUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.entity.SysRuntimeSetting;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.SysRuntimeSettingMapper;
import com.linkx.server.service.EmailService;
import com.linkx.server.service.linkmate.LinkMateLlmClient;
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.storage.StorageProviderType;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSettingServiceImplLinkMateTest {

    @Mock
    private SysRuntimeSettingMapper runtimeSettingMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private MailSenderHolder mailSenderHolder;
    @Mock
    private Environment environment;
    @Mock
    private ObjectStorageRouter objectStorageRouter;
    @Mock
    private LinkMateLlmClient linkMateLlmClient;
    @Mock
    private ImConversationMapper conversationMapper;

    private final LinkxProperties linkxProperties = new LinkxProperties();
    private AdminSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSettingServiceImpl(
                linkxProperties,
                runtimeSettingMapper,
                emailService,
                mailSenderHolder,
                environment,
                objectStorageRouter,
                linkMateLlmClient,
                conversationMapper);
    }

    @Test
    void updateLinkMate_persistsAgentAndGroupAiDefaults() {
        when(objectStorageRouter.activeProvider()).thenReturn(StorageProviderType.MINIO);
        SysRuntimeSetting existing = SysRuntimeSetting.builder()
                .id(SysRuntimeSetting.SINGLETON_ID)
                .linkmateApiKey("stored-key")
                .build();
        when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID)).thenReturn(existing);
        when(conversationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(12L, 9L, 3L, 2L);

        LinkMateSettingUpdateDTO dto = baseDto();
        dto.setAgentEnabled(false);
        dto.setGroupLinkmateDefaultEnabled(false);
        dto.setGroupAiProactiveDefaultEnabled(true);
        dto.setGroupAiSmartSummaryDefaultEnabled(true);
        dto.setGroupAiDefaultInterestTopics(" 产品发布 ");
        dto.setGroupAiDefaultSummaryInstruction(" 提炼待办 ");

        AdminSettingVO result = service.updateLinkMate(dto, 1L);

        ArgumentCaptor<SysRuntimeSetting> captor = ArgumentCaptor.forClass(SysRuntimeSetting.class);
        verify(runtimeSettingMapper).update(captor.capture());
        SysRuntimeSetting saved = captor.getValue();
        assertFalse(Boolean.TRUE.equals(saved.getLinkmateAgentEnabled()));
        assertFalse(Boolean.TRUE.equals(saved.getGroupLinkmateDefaultEnabled()));
        assertTrue(Boolean.TRUE.equals(saved.getGroupAiProactiveDefaultEnabled()));
        assertTrue(Boolean.TRUE.equals(saved.getGroupAiSmartSummaryDefaultEnabled()));
        assertEquals("产品发布", saved.getGroupAiDefaultInterestTopics());
        assertEquals("提炼待办", saved.getGroupAiDefaultSummaryInstruction());
        assertTrue(saved.getLinkmateReasoningSupported());

        assertFalse(linkxProperties.getLinkmate().isAgentEnabled());
        assertFalse(linkxProperties.getGroupAi().isLinkmateDefaultEnabled());
        assertTrue(linkxProperties.getGroupAi().isProactiveDefaultEnabled());
        assertTrue(linkxProperties.getGroupAi().isSmartSummaryDefaultEnabled());
        assertEquals("产品发布", linkxProperties.getGroupAi().getDefaultInterestTopics());
        assertEquals("提炼待办", linkxProperties.getGroupAi().getDefaultSummaryInstruction());

        assertNotNull(result.getLinkmate());
        assertFalse(Boolean.TRUE.equals(result.getLinkmate().getAgentEnabled()));
        assertNotNull(result.getLinkmate().getGroupAiDefaults());
        assertFalse(Boolean.TRUE.equals(result.getLinkmate().getGroupAiDefaults().getLinkmateEnabled()));
        assertTrue(Boolean.TRUE.equals(result.getLinkmate().getGroupAiDefaults().getProactiveEnabled()));
        assertEquals(12L, result.getLinkmate().getGroupAiOverview().getTotalGroups());
        assertEquals(9L, result.getLinkmate().getGroupAiOverview().getLinkmateEnabledGroups());
        assertEquals(3L, result.getLinkmate().getGroupAiOverview().getProactiveEnabledGroups());
        assertEquals(2L, result.getLinkmate().getGroupAiOverview().getSmartSummaryEnabledGroups());
    }

    @Test
    void updateLinkMate_rejectsEnableWithoutApiKey() {
        when(runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID))
                .thenReturn(SysRuntimeSetting.builder().id(SysRuntimeSetting.SINGLETON_ID).build());

        LinkMateSettingUpdateDTO dto = baseDto();
        dto.setEnabled(true);
        dto.setApiKey(null);

        CustomException ex = assertThrows(CustomException.class, () -> service.updateLinkMate(dto, 1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("API Key"));
    }

    private static LinkMateSettingUpdateDTO baseDto() {
        LinkMateSettingUpdateDTO dto = new LinkMateSettingUpdateDTO();
        dto.setEnabled(true);
        dto.setApiKey("test-key");
        dto.setBaseUrl("https://api.deepseek.com");
        dto.setModel("deepseek-chat");
        dto.setMaxTokens(4096);
        dto.setTemperature(0.7);
        dto.setDailyTokenLimit(100000);
        dto.setAgentEnabled(true);
        dto.setGroupLinkmateDefaultEnabled(true);
        dto.setGroupAiProactiveDefaultEnabled(false);
        dto.setGroupAiSmartSummaryDefaultEnabled(false);
        return dto;
    }
}
