package com.linkx.server.task.snailjob;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.task.AdminExportJobCleanupTask;
import com.linkx.server.task.AutoUnlockTask;
import com.linkx.server.task.FeedbackEscalationTask;
import com.linkx.server.task.GroupMuteTask;
import com.linkx.server.task.MessageRetentionTask;
import com.linkx.server.task.PresenceHeartbeatTask;
import com.linkx.server.task.ReviewEscalationTask;
import com.linkx.server.task.RedPacketTask;
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
}
