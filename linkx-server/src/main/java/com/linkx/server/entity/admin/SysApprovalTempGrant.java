package com.linkx.server.entity.admin;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_approval_temp_grant")
public class SysApprovalTempGrant {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long recordId;
    private Long userId;
    private String permissionCode;
    private Date grantedAt;
    private Date revokedAt;
}
