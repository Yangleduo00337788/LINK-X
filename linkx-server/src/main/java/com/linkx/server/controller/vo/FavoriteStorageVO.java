package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class FavoriteStorageVO {
    private Long usedBytes;
    private Long quotaBytes;
    private Integer itemCount;
    private Double usedPercent;
    /** all / link / image / file / note / message / other */
    private Map<String, Integer> typeCounts;
}
