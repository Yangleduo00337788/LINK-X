package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminVersionDTO;
import com.linkx.server.controller.admin.dto.AdminVersionQueryDTO;
import com.linkx.server.controller.admin.vo.AdminVersionVO;
import com.linkx.server.entity.admin.SysAppVersion;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysAppVersionMapper;
import com.linkx.server.service.admin.impl.AdminVersionServiceImpl;
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
@DisplayName("AdminVersionService 版本管理")
class AdminVersionServiceTest {

    @Mock SysAppVersionMapper versionMapper;
    @Mock AdminSettingService adminSettingService;

    private AdminVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminVersionServiceImpl(versionMapper, adminSettingService);
    }

    private SysAppVersion draftVersion(Long id) {
        return SysAppVersion.builder()
                .id(id)
                .version("1.0.0")
                .channel("stable")
                .releaseNotes("init")
                .downloadUrl("https://example.com/app")
                .forceUpdate(false)
                .minSupportedVersion("0.9.0")
                .status(SysAppVersion.STATUS_DRAFT)
                .deleted(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private AdminVersionDTO validDto() {
        AdminVersionDTO dto = new AdminVersionDTO();
        dto.setVersion("1.1.0");
        dto.setChannel("stable");
        dto.setReleaseNotes("bugfix");
        dto.setDownloadUrl("https://example.com/v1.1.0");
        dto.setForceUpdate(true);
        dto.setMinSupportedVersion("1.0.0");
        return dto;
    }

    @Test
    @DisplayName("列表分页与筛选")
    void list_ok() {
        when(versionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(versionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(draftVersion(1L)));

        AdminVersionQueryDTO q = new AdminVersionQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("1.0");
        q.setVersionStatus(SysAppVersion.STATUS_DRAFT);
        q.setChannel("stable");

        var page = service.list(q);
        assertEquals(1, page.getTotal());
        assertEquals("1.0.0", page.getItems().get(0).getVersion());
    }

    @Test
    @DisplayName("创建/更新/删除草稿")
    void create_update_delete() {
        when(versionMapper.insert(any(SysAppVersion.class))).thenAnswer(inv -> {
            SysAppVersion e = inv.getArgument(0);
            e.setId(9L);
            return 1;
        });
        AdminVersionVO created = service.create(validDto(), 1L);
        assertEquals(9L, created.getId());
        assertEquals(SysAppVersion.STATUS_DRAFT, created.getStatus());

        SysAppVersion draft = draftVersion(2L);
        when(versionMapper.selectOneById(2L)).thenReturn(draft);
        service.update(2L, validDto(), 3L);
        verify(versionMapper).update(draft);

        service.delete(2L, 3L);
        assertEquals(1, draft.getDeleted());
    }

    @Test
    @DisplayName("发布同步运行时配置并归档旧版本")
    void publish_syncs_runtime() {
        SysAppVersion draft = draftVersion(4L);
        SysAppVersion published = draftVersion(5L);
        published.setStatus(SysAppVersion.STATUS_PUBLISHED);
        when(versionMapper.selectOneById(4L)).thenReturn(draft);
        when(versionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(published));

        AdminVersionVO pub = service.publish(4L, 8L);
        assertEquals(SysAppVersion.STATUS_PUBLISHED, pub.getStatus());
        assertEquals(SysAppVersion.STATUS_ARCHIVED, published.getStatus());
        verify(adminSettingService).syncPublishedAppVersion(
                eq("1.0.0"), eq("stable"), eq("init"), eq("https://example.com/app"),
                eq(false), eq("0.9.0"), eq(8L));
        assertThrows(CustomException.class, () -> service.publish(4L, 8L));
    }

    @Test
    @DisplayName("已发布版本不可编辑删除")
    void published_guards() {
        SysAppVersion published = draftVersion(6L);
        published.setStatus(SysAppVersion.STATUS_PUBLISHED);
        when(versionMapper.selectOneById(6L)).thenReturn(published);
        assertThrows(CustomException.class, () -> service.update(6L, validDto(), 1L));
        assertThrows(CustomException.class, () -> service.delete(6L, 1L));
    }

    @Test
    @DisplayName("校验版本号与最低支持版本")
    void validation() {
        AdminVersionDTO bad = validDto();
        bad.setVersion("v1");
        assertThrows(CustomException.class, () -> service.create(bad, 1L));

        AdminVersionDTO minHigh = validDto();
        minHigh.setMinSupportedVersion("2.0.0");
        assertThrows(CustomException.class, () -> service.create(minHigh, 1L));

        when(versionMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(99L));
    }
}
