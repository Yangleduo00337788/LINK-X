package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;

final class MessageEncryptionTestSupport {

    static final String ACTIVE_KEK = "linkx-test-active-kek-32-chars-min!!";
    static final String LEGACY_KEK = "linkx-test-legacy-kek-32-chars-min!";

    private MessageEncryptionTestSupport() {
    }

    static MessageKekProvider activeOnlyProvider() {
        LinkxProperties props = baseProperties();
        props.getMessageEncryption().setKek(ACTIVE_KEK);
        props.getMessageEncryption().setKeyId("default");
        MessageKekProvider provider = new MessageKekProvider(props, new ObjectMapper());
        provider.reloadKeyCache();
        return provider;
    }

    static MessageKekProvider rotationProvider() {
        LinkxProperties props = baseProperties();
        props.getMessageEncryption().setKek(ACTIVE_KEK);
        props.getMessageEncryption().setKeyId("v2");
        props.getMessageEncryption().setLegacyKekMap(
                "{\"default\":\"" + LEGACY_KEK + "\"}");
        MessageKekProvider provider = new MessageKekProvider(props, new ObjectMapper());
        provider.reloadKeyCache();
        return provider;
    }

    private static LinkxProperties baseProperties() {
        LinkxProperties props = new LinkxProperties();
        props.getMessageEncryption().setEnabled(true);
        return props;
    }
}
