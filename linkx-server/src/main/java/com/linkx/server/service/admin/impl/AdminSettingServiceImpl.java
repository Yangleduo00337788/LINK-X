package com.linkx.server.service.admin.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.service.admin.AdminSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminSettingServiceImpl implements AdminSettingService {

    private final LinkxProperties linkxProperties;

    @Override
    public AdminSettingVO getSettings() {
        LinkxProperties.App app = linkxProperties.getApp();
        return AdminSettingVO.builder()
                .captchaEnabled(linkxProperties.getAuth().isCaptchaEnabled())
                .appVersion(app != null ? app.getVersion() : null)
                .appChannel(app != null ? app.getChannel() : null)
                .releaseNotes(app != null ? app.getReleaseNotes() : null)
                .downloadUrl(app != null ? app.getDownloadUrl() : null)
                .maxUploadBytes(linkxProperties.getMinio().getMaxFileSize())
                .build();
    }
}
