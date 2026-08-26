package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortVideoTranscodeSupportTest {

    @Test
    void activeStatuses() {
        assertTrue(ShortVideoTranscodeSupport.isActive("pending"));
        assertTrue(ShortVideoTranscodeSupport.isActive("processing"));
        assertFalse(ShortVideoTranscodeSupport.isActive("completed"));
        assertFalse(ShortVideoTranscodeSupport.isActive("skipped"));
        assertFalse(ShortVideoTranscodeSupport.isActive(null));
    }

    @Test
    void failedStatus() {
        assertTrue(ShortVideoTranscodeSupport.isFailed("failed"));
        assertFalse(ShortVideoTranscodeSupport.isFailed("pending"));
    }

    @Test
    void shouldShowStatus() {
        assertTrue(ShortVideoTranscodeSupport.shouldShowStatus("pending"));
        assertTrue(ShortVideoTranscodeSupport.shouldShowStatus("failed"));
        assertFalse(ShortVideoTranscodeSupport.shouldShowStatus("completed"));
        assertFalse(ShortVideoTranscodeSupport.shouldShowStatus("skipped"));
    }
}
