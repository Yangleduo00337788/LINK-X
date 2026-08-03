package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemConnectionPoolVO {

    private String poolName;
    private Integer activeConnections;
    private Integer idleConnections;
    private Integer totalConnections;
    private Integer maxConnections;
    private Integer threadsAwaitingConnection;
}
