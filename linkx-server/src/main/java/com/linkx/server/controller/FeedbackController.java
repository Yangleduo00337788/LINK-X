package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.FeedbackDTO;
import com.linkx.server.controller.dto.FeedbackFollowUpDTO;
import com.linkx.server.controller.vo.FeedbackReplyVO;
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
@Tag(name = "${openapi.tag.feedback}")
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;

    @Operation(summary = "提交意见反馈")
    @PostMapping
    public Result<FeedbackVO> submit(@Valid @RequestBody FeedbackDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        SysUser user = sysUserMapper.selectOneByQuery(
                QueryWrapper.create().where(SysUser::getId).eq(userId)
        );
        String username = user != null ? user.getUsername() : "unknown";

        Feedback feedback = feedbackService.create(userId, username, dto.getType(), dto.getContent(), dto.getContact());
        return Result.success(toVO(feedback, false));
    }

    @Operation(summary = "查询我的反馈列表")
    @GetMapping
    public Result<List<FeedbackVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        List<Feedback> list = feedbackService.listByUser(userId);
        return Result.success(list.stream().map(f -> toVO(f, false)).collect(Collectors.toList()));
    }

    @Operation(summary = "查询我的反馈详情（含多轮回复）")
    @GetMapping("/{id}")
    public Result<FeedbackVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(feedbackService.getDetail(userId, id));
    }

    @Operation(summary = "查询反馈回复记录")
    @GetMapping("/{id}/replies")
    public Result<List<FeedbackReplyVO>> listReplies(@PathVariable Long id, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(feedbackService.listReplies(userId, id));
    }

    @Operation(summary = "用户补充说明/追评")
    @PostMapping("/{id}/replies")
    public Result<FeedbackReplyVO> userReply(@PathVariable Long id,
                                             @Valid @RequestBody FeedbackFollowUpDTO dto,
                                             HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        SysUser user = sysUserMapper.selectOneByQuery(
                QueryWrapper.create().where(SysUser::getId).eq(userId)
        );
        String username = user != null ? user.getUsername() : "user";
        return Result.success(feedbackService.userReply(userId, username, id, dto.getContent()));
    }

    private FeedbackVO toVO(Feedback feedback, boolean withReplies) {
        if (withReplies) {
            return feedbackService.getDetail(feedback.getUserId(), feedback.getId());
        }
        return FeedbackVO.builder()
                .id(feedback.getId())
                .type(feedback.getType())
                .content(feedback.getContent())
                .status(feedback.getStatus())
                .reply(feedback.getReply())
                .replyTime(feedback.getReplyTime())
                .createTime(feedback.getCreateTime())
                .build();
    }
}
