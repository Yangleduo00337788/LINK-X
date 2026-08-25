package com.linkx.server.task.snailjob;


/**
 * 作者：yangleduo
 */
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.task.AdminExportJobCleanupTask;
import com.linkx.server.task.AutoUnlockTask;
import com.linkx.server.task.FeedbackEscalationTask;
import com.linkx.server.task.GroupMuteTask;
import com.linkx.server.task.MessageContentKeyRotationTask;
import com.linkx.server.task.MessageContentReencryptTask;
import com.linkx.server.task.MessageRetentionTask;
import com.linkx.server.task.PresenceHeartbeatTask;
import com.linkx.server.task.ReviewEscalationTask;
import com.linkx.server.task.RedPacketTask;
import com.linkx.server.task.SearchTextBackfillTask;
import com.linkx.server.task.ShortVideoTranscodeTask;
import com.linkx.server.task.StatisticSnapshotTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LinkX 内置 SnailJob 执行器，名称与 {@link SnailJobJobCatalog} 保持一致。
 */
public final class LinkxSnailJobExecutors {

    private LinkxSnailJobExecutors() {
    }

    @Component
    @JobExecutor(name = "feedback_escalation")
    @RequiredArgsConstructor
    public static class FeedbackEscalationJobExecutor {
        private final FeedbackEscalationTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.scanOverdueFeedback();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "review_escalation")
    @RequiredArgsConstructor
    public static class ReviewEscalationJobExecutor {
        private final ReviewEscalationTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.scanOverdueReviews();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "red_packet_expire")
    @RequiredArgsConstructor
    public static class RedPacketExpireJobExecutor {
        private final RedPacketTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.expireRedPackets();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "message_content_key_rotate")
    @RequiredArgsConstructor
    public static class MessageContentKeyRotationJobExecutor {
        private final MessageContentKeyRotationTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            MessageContentKeyRotationTask.RotationResult result = delegate.rotateBatch();
            if (result.wasSkipped()) {
                return ExecuteResult.success("skipped: encryption disabled");
            }
            return ExecuteResult.success(
                    "updated=" + result.updated() + ", remaining=" + result.remaining());
        }
    }

    @Component
    @JobExecutor(name = "message_content_reencrypt")
    @RequiredArgsConstructor
    public static class MessageContentReencryptJobExecutor {
        private final MessageContentReencryptTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            MessageContentReencryptTask.ReencryptResult result = delegate.reencryptBatch();
            if (result.wasSkipped()) {
                return ExecuteResult.success("skipped: encryption disabled");
            }
            return ExecuteResult.success(
                    "updated=" + result.updated() + ", remaining=" + result.remaining());
        }
    }

    @Component
    @JobExecutor(name = "search_text_backfill")
    @RequiredArgsConstructor
    public static class SearchTextBackfillJobExecutor {
        private final SearchTextBackfillTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            SearchTextBackfillTask.BackfillResult result = delegate.backfillBatch();
            return ExecuteResult.success(
                    "updated=" + result.updated() + ", remaining=" + result.remaining());
        }
    }

    @Component
    @JobExecutor(name = "message_retention")
    @RequiredArgsConstructor
    public static class MessageRetentionJobExecutor {
        private final MessageRetentionTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.purgeExpiredMessages();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "group_mute")
    @RequiredArgsConstructor
    public static class GroupMuteJobExecutor {
        private final GroupMuteTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.applyMuteSchedules();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "admin_export_cleanup")
    @RequiredArgsConstructor
    public static class AdminExportCleanupJobExecutor {
        private final AdminExportJobCleanupTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.expireStaleJobs();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "auto_unlock")
    @RequiredArgsConstructor
    public static class AutoUnlockJobExecutor {
        private final AutoUnlockTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.unlockExpired();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "presence_heartbeat")
    @RequiredArgsConstructor
    public static class PresenceHeartbeatJobExecutor {
        private final PresenceHeartbeatTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.heartbeatAndSweep();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "sensitive_word_refresh")
    @RequiredArgsConstructor
    public static class SensitiveWordRefreshJobExecutor {
        private final SensitiveWordService delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.refreshDictionary();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "statistic_snapshot_daily")
    @RequiredArgsConstructor
    public static class StatisticSnapshotDailyJobExecutor {
        private final StatisticSnapshotTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            delegate.captureDailySnapshots();
            return ExecuteResult.success();
        }
    }

    @Component
    @JobExecutor(name = "short_video_transcode")
    @RequiredArgsConstructor
    public static class ShortVideoTranscodeJobExecutor {
        private final ShortVideoTranscodeTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            int processed = delegate.processPending();
            return ExecuteResult.success("processed=" + processed);
        }
    }

    @Component
    @JobExecutor(name = "statistic_snapshot_backfill")
    @RequiredArgsConstructor
    public static class StatisticSnapshotBackfillJobExecutor {
        private final StatisticSnapshotTask delegate;

        public ExecuteResult jobExecute(JobArgs jobArgs) {
            int days = 30;
            if (jobArgs != null && jobArgs.getJobParams() != null) {
                String params = String.valueOf(jobArgs.getJobParams()).trim();
                if (!params.isEmpty()) {
                    try {
                        days = Integer.parseInt(params);
                    } catch (NumberFormatException ignored) {
                        // 使用默认 30 天
                    }
                }
            }
            delegate.backfillSnapshots(days);
            return ExecuteResult.success();
        }
    }
}
