package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortVideoTopicHotScoreTest {

    @Test
    void zeroPostCountReturnsZero() {
        assertEquals(0D, ShortVideoTopicHotScore.compute(0, new Date()));
    }

    @Test
    void staleTopicUsesLowBaseScore() {
        assertEquals(2.5D, ShortVideoTopicHotScore.compute(25, null));
    }

    @Test
    void freshTopicGetsBoost() {
        Date recent = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        double score = ShortVideoTopicHotScore.compute(10, recent);
        assertTrue(score > 10D);
    }

    @Test
    void olderTopicScoresLowerThanFresh() {
        Date recent = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));
        Date old = Date.from(Instant.now().minus(72, ChronoUnit.HOURS));
        double freshScore = ShortVideoTopicHotScore.compute(10, recent);
        double oldScore = ShortVideoTopicHotScore.compute(10, old);
        assertTrue(freshScore > oldScore);
    }
}
