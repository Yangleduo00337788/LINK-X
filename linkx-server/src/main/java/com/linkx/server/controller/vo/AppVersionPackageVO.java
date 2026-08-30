package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionPackageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String packageFormat;
    private String downloadUrl;
    private String packageFileName;
    private String packageSha256;
    private Long packageSize;
}
