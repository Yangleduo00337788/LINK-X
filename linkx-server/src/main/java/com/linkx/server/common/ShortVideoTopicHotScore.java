package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** 短视频话题热度：作品数 × 时间衰减，近期有更新的话题额外加权。 */
public final class ShortVideoTopicHotScore {

    private static final double DECAY_HOURS = 24.0D;
    private static final double FRESH_HOURS = 6.0D;
    private static final double FRESH_BOOST = 1.25D;
    private static final double STALE_FACTOR = 0.1D;

    private ShortVideoTopicHotScore() {
    }

    public static double compute(int postCount, Date lastPostAt) {
        if (postCount <= 0) {
            return 0D;
        }
        if (lastPostAt == null) {
            return postCount * STALE_FACTOR;
        }
        double hours = Math.max(
                0D,
                Duration.between(lastPostAt.toInstant(), Instant.now()).toHours());
        double decay = 1D / (1D + hours / DECAY_HOURS);
        double score = postCount * decay;
        if (hours <= FRESH_HOURS) {
            score *= FRESH_BOOST;
        }
        return score;
    }
}
