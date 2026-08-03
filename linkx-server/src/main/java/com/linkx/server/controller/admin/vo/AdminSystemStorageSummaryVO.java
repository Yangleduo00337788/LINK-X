package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemStorageSummaryVO {

    private int tableCount;
    private long approximateRowCount;
    private long dataBytes;
    private long indexBytes;
    private long totalBytes;
}
