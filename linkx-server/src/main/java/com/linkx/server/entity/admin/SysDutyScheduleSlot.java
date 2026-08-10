package com.linkx.server.entity.admin;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.sql.Time;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_duty_schedule_slot")
public class SysDutyScheduleSlot implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long scheduleId;
    private Integer weekday;
    private Time startTime;
    private Time endTime;
    private Long assigneeId;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
