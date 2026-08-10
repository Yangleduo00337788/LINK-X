package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysDeviceBan;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserDeviceBinding;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeviceBanMapper;
import com.linkx.server.mapper.SysUserDeviceBindingMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.DeviceSecurityService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DeviceSecurityServiceImpl implements DeviceSecurityService {

    private final SysDeviceBanMapper deviceBanMapper;
    private final SysUserDeviceBindingMapper deviceBindingMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public void assertDeviceAllowed(Long userId, String deviceId) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return;
        }
        String normalized = deviceId.trim();
        if (isBanned(userId, normalized)) {
            throw new CustomException(403, "该设备已被封禁，无法登录");
        }
        if (isBindingEnabled(userId) && !isApproved(userId, normalized)) {
            throw new CustomException(403, "该账号已开启设备强绑定，当前设备未获批准");
        }
    }

    @Override
    public boolean isBanned(Long userId, String deviceId) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return false;
        }
        long count = deviceBanMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysDeviceBan::getUserId).eq(userId)
                        .and(SysDeviceBan::getDeviceId).eq(deviceId.trim())
                        .and(SysDeviceBan::getStatus).eq(SysDeviceBan.STATUS_ACTIVE)
        );
        return count > 0;
    }

    @Override
    public boolean isBindingEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = sysUserMapper.selectOneById(userId);
        return user != null && Integer.valueOf(1).equals(user.getDeviceBindingEnabled());
    }

    @Override
    public boolean isApproved(Long userId, String deviceId) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return false;
        }
        long count = deviceBindingMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserDeviceBinding::getUserId).eq(userId)
                        .and(SysUserDeviceBinding::getDeviceId).eq(deviceId.trim())
        );
        return count > 0;
    }
}
