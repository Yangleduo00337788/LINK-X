package com.linkx.server.controller;

import com.linkx.server.common.ImageUploadValidator;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.common.UserProfileMapper;
import com.linkx.server.controller.dto.ChangePasswordDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.dto.UserPreferenceDTO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.controller.vo.UserPreferenceVO;
import com.linkx.server.controller.vo.UserProfileVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.UserPreference;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.SysUserService;
import com.linkx.server.service.UserPreferenceService;
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
    private final DeviceSessionService deviceSessionService;
    private final UserPreferenceService userPreferenceService;
    private final JwtUtils jwtUtils;

    private static final String DEFAULT_DEVICE_ID = "default-web-device";
    private static final String DEFAULT_DEVICE_NAME = "Web 浏览器";
    private static final String DEFAULT_DEVICE_TYPE = "Web";

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

        try {
            ImageUploadValidator.assertSupportedImage(file);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }

        // 上传文件（私有桶，传入 null 让 service 用 UUID 命名）
        String objectKey = fileStorageService.uploadFile(file, null);

        // 生成 24 小时有效的签名 URL 返回给前端
        String signedUrl = fileStorageService.getPresignedUrl(objectKey, 24 * 3600);

        // 将对象 key 存入数据库（不存 URL）
        sysUserService.updateAvatar(userId, objectKey);

        return Result.success(signedUrl);
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

        return Result.success(buildUserProfileVO(user));
    }

    /**
     * 构建 UserProfileVO（把数据库里的对象 key 转成签名 URL）
     */
    private UserProfileVO buildUserProfileVO(SysUser user) {
        UserProfileVO vo = UserProfileMapper.toProfileVO(user);
        if (vo != null) {
            vo.setAvatar(toSignedUrl(vo.getAvatar(), 24 * 3600));
        }
        return vo;
    }

    /**
     * 把对象 key 转签名 URL。若传入的是已存在的 URL（含 http）或 null/空，则原样返回
     */
    private String toSignedUrl(String objectKeyOrUrl, int expiry) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isEmpty()) {
            return objectKeyOrUrl;
        }
        if (objectKeyOrUrl.startsWith("http://") || objectKeyOrUrl.startsWith("https://")) {
            return objectKeyOrUrl;
        }
        return fileStorageService.getPresignedUrl(objectKeyOrUrl, expiry);
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordDTO dto,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        sysUserService.changePassword(userId, dto.getOldPassword(), dto.getNewPassword());
        return Result.success(null);
    }

    /**
     * 获取登录设备列表
     */
    @GetMapping("/devices")
    public Result<java.util.List<DeviceVO>> listDevices(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        // 从请求头或 Token 中获取当前设备 ID（简化实现，使用默认设备）
        String currentDeviceId = request.getHeader("X-Device-Id");
        if (currentDeviceId == null || currentDeviceId.isBlank()) {
            currentDeviceId = DEFAULT_DEVICE_ID;
        }
        return Result.success(deviceSessionService.listByUser(userId, currentDeviceId));
    }

    /**
     * 强制下线指定设备
     */
    @DeleteMapping("/devices/{deviceId}")
    public Result<Void> logoutDevice(
            @PathVariable String deviceId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        deviceSessionService.deleteDevice(userId, deviceId);
        return Result.success(null);
    }

    /**
     * 获取当前用户的偏好设置。
     * 若尚无记录，返回与表默认值一致的虚拟对象（不写入库）。
     */
    @GetMapping("/preference")
    public Result<UserPreferenceVO> getPreference(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(toPreferenceVO(userPreferenceService.getOrDefault(userId)));
    }

    /**
     * 局部更新当前用户的偏好设置（PUT 语义）。
     * 请求体中为 null 的字段保持不变；语言/聊天背景为空字符串也视为"不修改"。
     */
    @PutMapping("/preference")
    public Result<UserPreferenceVO> updatePreference(
            @RequestBody UserPreferenceDTO dto,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        UserPreference patch = UserPreference.builder()
                .autoStart(dto.getAutoStart())
                .soundNotify(dto.getSoundNotify())
                .messageDetail(dto.getMessageDetail())
                .notifyAtMe(dto.getNotifyAtMe())
                .notifySound(dto.getNotifySound())
                .privacyVerifyFriend(dto.getPrivacyVerifyFriend())
                .privacyAllowStranger(dto.getPrivacyAllowStranger())
                .privacyShowOnline(dto.getPrivacyShowOnline())
                .language(emptyToNull(dto.getLanguage()))
                .chatBackground(emptyToNull(dto.getChatBackground()))
                .notifyTone(emptyToNull(dto.getNotifyTone()))
                .build();
        UserPreference saved = userPreferenceService.upsert(userId, patch);
        return Result.success(toPreferenceVO(saved));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static UserPreferenceVO toPreferenceVO(UserPreference p) {
        if (p == null) return null;
        return UserPreferenceVO.builder()
                .autoStart(p.getAutoStart())
                .soundNotify(p.getSoundNotify())
                .messageDetail(p.getMessageDetail())
                .notifyAtMe(p.getNotifyAtMe())
                .notifySound(p.getNotifySound())
                .privacyVerifyFriend(p.getPrivacyVerifyFriend())
                .privacyAllowStranger(p.getPrivacyAllowStranger())
                .privacyShowOnline(p.getPrivacyShowOnline())
                .language(p.getLanguage())
                .chatBackground(p.getChatBackground())
                .notifyTone(p.getNotifyTone())
                .build();
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
