package com.linkx.server.im;

import io.netty.util.AttributeKey;

public final class ImChannelAttributes {

    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");
    /** Presence Redis SET 成员用的连接唯一 ID */
    public static final AttributeKey<String> PRESENCE_CONN_ID = AttributeKey.valueOf("presenceConnId");

    private ImChannelAttributes() {
    }
}
