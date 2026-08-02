package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminSensitiveWordDTO;
import com.linkx.server.entity.SysSensitiveWord;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SensitiveWordMapper;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.impl.AdminSensitiveWordServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSensitiveWordService 敏感词")
class AdminSensitiveWordServiceTest {

    @Mock SensitiveWordMapper sensitiveWordMapper;
    @Mock SensitiveWordService sensitiveWordService;

    private AdminSensitiveWordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSensitiveWordServiceImpl(sensitiveWordMapper, sensitiveWordService);
    }

    private SysSensitiveWord word(Long id, String action) {
        return SysSensitiveWord.builder()
                .id(id)
                .word("bad" + id)
                .category("general")
                .action(action)
                .replacement(SysSensitiveWord.ACTION_FILTER.equals(action) ? "***" : "")
                .enabled(true)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    @Test
    @DisplayName("列表与详情")
    void list_and_detail() {
        when(sensitiveWordMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(sensitiveWordMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(word(1L, SysSensitiveWord.ACTION_BLOCK)));

        AdminPageQueryDTO q = new AdminPageQueryDTO();
        q.setPage(1);
        q.setSize(20);
        q.setKeyword("bad");
        q.setStatus(1);
        assertEquals(1, service.list(q).getTotal());
        assertNull(service.list(q).getItems().get(0).getReplacement());

        when(sensitiveWordMapper.selectOneById(1L)).thenReturn(word(1L, SysSensitiveWord.ACTION_FILTER));
        assertEquals("***", service.detail(1L).getReplacement());

        when(sensitiveWordMapper.selectOneById(404L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(404L));
    }

    @Test
    @DisplayName("创建 filter/block/alert 策略")
    void create_actions() {
        when(sensitiveWordMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(sensitiveWordMapper.insert(any(SysSensitiveWord.class))).thenAnswer(inv -> {
            SysSensitiveWord w = inv.getArgument(0);
            w.setId(10L);
            return 1;
        });

        AdminSensitiveWordDTO filter = dto("spam", SysSensitiveWord.ACTION_FILTER, "general", "***");
        assertEquals("***", service.create(filter, 1L).getReplacement());
        verify(sensitiveWordService).refreshDictionary();

        AdminSensitiveWordDTO block = dto("violence", SysSensitiveWord.ACTION_BLOCK, "violence", null);
        assertNull(service.create(block, 1L).getReplacement());

        AdminSensitiveWordDTO alert = dto("adword", SysSensitiveWord.ACTION_ALERT, "unknown_cat", null);
        assertEquals("general", service.create(alert, 1L).getCategory());
    }

    @Test
    @DisplayName("更新与删除")
    void update_and_delete() {
        SysSensitiveWord existing = word(2L, SysSensitiveWord.ACTION_ALERT);
        when(sensitiveWordMapper.selectOneById(2L)).thenReturn(existing);
        when(sensitiveWordMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);

        AdminSensitiveWordDTO dto = dto("bad2", SysSensitiveWord.ACTION_FILTER, "ad", null);
        dto.setEnabled(false);
        var updated = service.update(2L, dto, 1L);
        assertFalse(updated.getEnabled());
        assertEquals("***", updated.getReplacement());
        verify(sensitiveWordMapper).update(existing);
        verify(sensitiveWordService, times(1)).refreshDictionary();

        service.delete(2L, 1L);
        verify(sensitiveWordMapper).deleteById(2L);
        verify(sensitiveWordService, times(2)).refreshDictionary();
    }

    @Test
    @DisplayName("校验：空词/重复/非法 action")
    void validation() {
        AdminSensitiveWordDTO empty = new AdminSensitiveWordDTO();
        empty.setAction(SysSensitiveWord.ACTION_BLOCK);
        assertThrows(CustomException.class, () -> service.create(empty, 1L));

        when(sensitiveWordMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        AdminSensitiveWordDTO dup = dto("dup", SysSensitiveWord.ACTION_BLOCK, "general", null);
        assertThrows(CustomException.class, () -> service.create(dup, 1L));

        AdminSensitiveWordDTO badAction = dto("x", "purge", "general", null);
        when(sensitiveWordMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        assertThrows(CustomException.class, () -> service.create(badAction, 1L));
    }

    private AdminSensitiveWordDTO dto(String word, String action, String category, String replacement) {
        AdminSensitiveWordDTO dto = new AdminSensitiveWordDTO();
        dto.setWord(word);
        dto.setAction(action);
        dto.setCategory(category);
        dto.setReplacement(replacement);
        dto.setEnabled(true);
        return dto;
    }
}
