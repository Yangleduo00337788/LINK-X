package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.FeedbackDTO;
import com.linkx.server.controller.vo.FeedbackVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.FeedbackService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;

    @PostMapping
    public Result<FeedbackVO> submit(@Valid @RequestBody FeedbackDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        SysUser user = sysUserMapper.selectOneByQuery(
                QueryWrapper.create().where(SysUser::getId).eq(userId)
        );
        String username = user != null ? user.getUsername() : "unknown";

        Feedback feedback = feedbackService.create(userId, username, dto.getType(), dto.getContent(), dto.getContact());
        return Result.success(toVO(feedback));
    }

    @GetMapping
    public Result<List<FeedbackVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        List<Feedback> list = feedbackService.listByUser(userId);
        return Result.success(list.stream().map(this::toVO).collect(Collectors.toList()));
    }

    private FeedbackVO toVO(Feedback feedback) {
        return FeedbackVO.builder()
                .id(feedback.getId())
                .type(feedback.getType())
                .content(feedback.getContent())
                .status(feedback.getStatus())
                .createTime(feedback.getCreateTime())
                .build();
    }
}
