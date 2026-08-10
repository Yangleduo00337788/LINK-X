package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.dto.AdminStepUpRequestDTO;
import com.linkx.server.controller.admin.dto.AdminStepUpVerifyDTO;
import com.linkx.server.controller.admin.vo.AdminStepUpChallengeVO;
import com.linkx.server.controller.admin.vo.AdminStepUpTokenVO;

public interface AdminStepUpService {

    boolean isEnabled();

    AdminStepUpChallengeVO options(Long userId, String action);

    AdminStepUpChallengeVO request(Long userId, AdminStepUpRequestDTO dto);

    AdminStepUpTokenVO verify(Long userId, AdminStepUpVerifyDTO dto);

    /**
     * 校验并消费 step-up token（单次有效）。
     *
     * @return true 表示通过；false 表示无效/不匹配
     */
    boolean consumeToken(Long userId, String token, String action);
}
