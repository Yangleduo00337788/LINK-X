package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriveStorageVO {
    private long usedBytes;
    private long quotaBytes;
    private int fileCount;
    private double usedPercent;
}
