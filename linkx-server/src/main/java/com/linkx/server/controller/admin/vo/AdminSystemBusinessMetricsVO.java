package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemBusinessMetricsVO {

    private long loginSuccess;
    private long loginFailure;
    private long registerSuccess;
    private long registerFailure;
    private long messageSent;
    private long fileUploadSuccess;
    private long fileUploadFailure;
    private long tokenRefreshSuccess;
    private long tokenRefreshFailure;
}
