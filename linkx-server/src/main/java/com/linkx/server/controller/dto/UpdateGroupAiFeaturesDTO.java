package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import lombok.Data;

@Data
public class UpdateGroupAiFeaturesDTO {

    /** 主动发言开关 */
    private Boolean proactiveEnabled;

    /** 关注话题（最多 200 字） */
    private String interestTopics;

    /** 智能总结开关 */
    private Boolean smartSummaryEnabled;

    /** 总结指令（最多 500 字） */
    private String summaryInstruction;
}
