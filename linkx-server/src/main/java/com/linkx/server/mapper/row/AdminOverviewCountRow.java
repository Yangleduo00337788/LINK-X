package com.linkx.server.mapper.row;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOverviewCountRow {

    private Long totalUsers;
    private Long activeUsers;
    private Long pendingFeedback;
    private Long todayNewUsers;
    private Long todayMessages;
    private Long todayLogins;
    private Long totalMessages;
    private Long totalUploads;
    private Long closedFeedback;
}
