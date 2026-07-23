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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConferenceInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String type;
    private Long creatorId;
    private Long conversationId;
    private Integer status;
    private Integer maxParticipants;
    private Date startTime;
    private Date endTime;
    private String callId;

    @Builder.Default
    private List<Map<String, Object>> participants = new ArrayList<>();
}
