package com.linkx.server.controller;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.common.UserProfileMapper;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.UserProfileVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;
    private final FileStorageService fileStorageService;
    private final JwtUtils jwtUtils;

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserProfileVO> getCurrentUser(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        return Result.success(buildUserProfileVO(user));
    }

    /**
     * 更新用户资料
     */
    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(
            @Valid @RequestBody UpdateProfileDTO dto,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        SysUser updatedUser = sysUserService.updateProfile(userId, dto);
        return Result.success(buildUserProfileVO(updatedUser));
    }

    /**
     * 上传用户头像
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(400, "只支持图片文件");
        }

        // 生成头像文件名
        String extension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = "avatar/" + userId + "/" + System.currentTimeMillis() + extension;

        // 上传文件
        String avatarUrl = fileStorageService.uploadFile(file, fileName);

        // 更新用户头像
        sysUserService.updateAvatar(userId, avatarUrl);

        return Result.success(avatarUrl);
    }

    /**
     * 获取用户公开资料（供其他用户查看）
     */
    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        return Result.success(UserProfileMapper.toProfileVO(user));
    }

    /**
     * 构建 UserProfileVO
     */
    private UserProfileVO buildUserProfileVO(SysUser user) {
        return UserProfileMapper.toProfileVO(user);
    }

    /**
     * 从拦截器写入的 request 属性或 Authorization 头解析当前用户 ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr instanceof Long userId) {
            return userId;
        }

        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            return jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }
}
