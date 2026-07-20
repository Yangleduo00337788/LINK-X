package com.linkx.server.service.impl;

import com.linkx.server.entity.UserPreference;
import com.linkx.server.mapper.UserPreferenceMapper;
import com.linkx.server.service.UserPreferenceService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 用户偏好设置 Service 实现
 * <p>
 * 实现策略：
 * - 读取：行不存在时返回内存默认值（与表默认值一致），不主动落库，避免空行；
 * - 写入：行不存在则插入（仅含非空字段），已存在则按非空字段覆盖；
 * - patch 中为 null 的字段表示"不修改"，不重置为默认值。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl
        extends ServiceImpl<UserPreferenceMapper, UserPreference>
        implements UserPreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;

    @Override
    public UserPreference getOrDefault(Long userId) {
        if (userId == null) return defaultPreference(null);
        UserPreference existing = userPreferenceMapper.selectOneById(userId);
        return existing != null ? existing : defaultPreference(userId);
    }

    @Override
    public UserPreference upsert(Long userId, UserPreference patch) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        UserPreference existing = userPreferenceMapper.selectOneById(userId);
        if (existing == null) {
            // 行不存在：从默认值开始，再用 patch 中显式给出的字段覆盖
            UserPreference fresh = defaultPreference(userId);
            applyPatch(fresh, patch);
            userPreferenceMapper.insert(fresh);
            return fresh;
        }
        applyPatch(existing, patch);
        updateById(existing);
        return existing;
    }

    @Override
    public boolean requiresFriendVerify(Long userId) {
        // 默认需要验证；仅显式 false 时关闭
        return !Boolean.FALSE.equals(getOrDefault(userId).getPrivacyVerifyFriend());
    }

    @Override
    public boolean allowsStrangerChat(Long userId) {
        return Boolean.TRUE.equals(getOrDefault(userId).getPrivacyAllowStranger());
    }

    @Override
    public boolean showsOnlineStatus(Long userId) {
        return !Boolean.FALSE.equals(getOrDefault(userId).getPrivacyShowOnline());
    }

    @Override
    public Map<Long, Boolean> batchShowsOnlineStatus(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : userIds) {
            if (id != null) {
                result.put(id, true); // 默认可见
            }
        }
        List<UserPreference> rows = userPreferenceMapper.selectListByQuery(
                QueryWrapper.create().where(UserPreference::getUserId).in(userIds)
        );
        for (UserPreference row : rows) {
            if (row.getUserId() == null) continue;
            result.put(row.getUserId(), !Boolean.FALSE.equals(row.getPrivacyShowOnline()));
        }
        return result;
    }

    /**
     * 将 patch 中非空字段合并到 target（PUT 语义）。
     * 注意：调用方需保证 target 已包含合理的初始值（如默认值或数据库现有行）。
     */
    private void applyPatch(UserPreference target, UserPreference patch) {
        if (patch == null) return;
        if (patch.getAutoStart() != null) target.setAutoStart(patch.getAutoStart());
        if (patch.getSoundNotify() != null) target.setSoundNotify(patch.getSoundNotify());
        if (patch.getMessageDetail() != null) target.setMessageDetail(patch.getMessageDetail());
        if (patch.getNotifyAtMe() != null) target.setNotifyAtMe(patch.getNotifyAtMe());
        if (patch.getNotifySound() != null) target.setNotifySound(patch.getNotifySound());
        if (patch.getPrivacyVerifyFriend() != null) target.setPrivacyVerifyFriend(patch.getPrivacyVerifyFriend());
        if (patch.getPrivacyAllowStranger() != null) target.setPrivacyAllowStranger(patch.getPrivacyAllowStranger());
        if (patch.getPrivacyShowOnline() != null) target.setPrivacyShowOnline(patch.getPrivacyShowOnline());
        if (patch.getLanguage() != null) target.setLanguage(patch.getLanguage());
        if (patch.getChatBackground() != null) target.setChatBackground(patch.getChatBackground());
        if (patch.getNotifyTone() != null) target.setNotifyTone(patch.getNotifyTone());
        if (patch.getMomentsBackground() != null) target.setMomentsBackground(patch.getMomentsBackground());
    }

    /** 生成与表默认值一致的偏好对象（不包含时间戳，由 MyBatis-Flex 填充） */
    private UserPreference defaultPreference(Long userId) {
        return UserPreference.builder()
                .userId(userId)
                .autoStart(false)
                .soundNotify(true)
                .messageDetail(true)
                .notifyAtMe(true)
                .notifySound(false)
                .privacyVerifyFriend(true)
                .privacyAllowStranger(false)
                .privacyShowOnline(true)
                .language("zh-CN")
                .chatBackground("default")
                .notifyTone("default")
                .build();
    }
}
