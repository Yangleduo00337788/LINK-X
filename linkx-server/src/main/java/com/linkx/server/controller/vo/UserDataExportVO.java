package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户数据导出（GDPR 数据主体访问）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDataExportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Date exportTime;

    @Builder.Default
    private List<Map<String, Object>> friends = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> conversations = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> recentMessages = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> devices = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> notes = new ArrayList<>();
}
