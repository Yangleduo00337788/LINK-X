package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "安装包直传分片预签名")
public class AdminVersionDirectPresignPartsVO {

    private int chunkSize;
    private List<PartUrl> parts;

    @Data
    @Builder
    public static class PartUrl {
        private int partNumber;
        private String url;
    }
}
