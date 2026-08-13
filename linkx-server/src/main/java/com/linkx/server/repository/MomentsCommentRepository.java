package com.linkx.server.repository;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.MomentsComment;
import com.linkx.server.mapper.MomentsCommentMapper;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 朋友圈评论持久化：写入前加密、读出后解密，业务层始终处理明文。
 */
@Repository
@RequiredArgsConstructor
public class MomentsCommentRepository {

    private final MomentsCommentMapper commentMapper;
    private final MessageContentCipher messageContentCipher;

    public void insert(MomentsComment comment) {
        MomentsComment stored = copyForPersistence(comment);
        commentMapper.insert(stored);
        comment.setId(stored.getId());
    }

    public MomentsComment selectOneById(Long id) {
        return decrypt(commentMapper.selectOneById(id));
    }

    public List<MomentsComment> selectListByQuery(QueryWrapper query) {
        List<MomentsComment> rows = commentMapper.selectListByQuery(query);
        messageContentCipher.decryptMomentsCommentFields(rows);
        return rows;
    }

    public List<MomentsComment> selectListByQueryWithoutDecrypt(QueryWrapper query) {
        return commentMapper.selectListByQuery(query);
    }

    public long countPendingReencrypt() {
        return commentMapper.selectCountByQuery(pendingReencryptQuery());
    }

    public int reencryptPlaintextBatch(int batchSize) {
        if (!messageContentCipher.isEnabled() || batchSize <= 0) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 1), 5000);
        List<MomentsComment> rows = commentMapper.selectListByQuery(
                pendingReencryptQuery().orderBy(MomentsComment::getId, true).limit(limit));
        int updated = 0;
        for (MomentsComment row : rows) {
            if (reencryptRow(row)) {
                updated++;
            }
        }
        return updated;
    }

    public long countPendingKeyRotation() {
        if (!messageContentCipher.isEnabled()) {
            return 0;
        }
        return commentMapper.selectCountByQuery(pendingKeyRotationQuery());
    }

    public int rotateEncryptedKeyBatch(int batchSize) {
        if (!messageContentCipher.isEnabled() || batchSize <= 0) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 1), 5000);
        List<MomentsComment> rows = commentMapper.selectListByQuery(
                pendingKeyRotationQuery().orderBy(MomentsComment::getId, true).limit(limit));
        int updated = 0;
        for (MomentsComment row : rows) {
            if (rotateKeyRow(row)) {
                updated++;
            }
        }
        return updated;
    }

    private boolean reencryptRow(MomentsComment row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        if (!messageContentCipher.needsContentReencrypt(row.getContent(), row.getContentEncVersion())) {
            if (messageContentCipher.isEncryptedContent(row.getContent(), row.getContentEncVersion())
                    && (row.getContentEncVersion() == null || row.getContentEncVersion() == 0)) {
                UpdateChain.of(MomentsComment.class)
                        .set(MomentsComment::getContentEncVersion, MessageContentCipher.ENC_VERSION)
                        .where(MomentsComment::getId).eq(row.getId())
                        .update();
                return true;
            }
            return false;
        }
        UpdateChain.of(MomentsComment.class)
                .set(MomentsComment::getContent,
                        messageContentCipher.encryptPlaintextForStorage(row.getContent()))
                .set(MomentsComment::getContentEncVersion, MessageContentCipher.ENC_VERSION)
                .where(MomentsComment::getId).eq(row.getId())
                .update();
        return true;
    }

    private boolean rotateKeyRow(MomentsComment row) {
        if (row == null || row.getId() == null
                || !messageContentCipher.needsKeyRotation(row.getContent())) {
            return false;
        }
        UpdateChain.of(MomentsComment.class)
                .set(MomentsComment::getContent,
                        messageContentCipher.rotateCiphertextForStorage(row.getContent()))
                .set(MomentsComment::getContentEncVersion, MessageContentCipher.ENC_VERSION)
                .where(MomentsComment::getId).eq(row.getId())
                .update();
        return true;
    }

    private QueryWrapper pendingKeyRotationQuery() {
        String currentPrefix = messageContentCipher.currentKeyPrefix();
        return QueryWrapper.create().where(
                "content_enc_version = 1 AND content LIKE 'lxenc:v1:%' AND content NOT LIKE ?",
                currentPrefix + "%");
    }

    private static QueryWrapper pendingReencryptQuery() {
        return QueryWrapper.create().where(
                "content_enc_version = 0 AND content IS NOT NULL AND TRIM(content) <> ''");
    }

    private MomentsComment copyForPersistence(MomentsComment source) {
        MomentsComment copy = cloneComment(source);
        messageContentCipher.encryptMomentsCommentFields(copy);
        return copy;
    }

    private MomentsComment decrypt(MomentsComment comment) {
        if (comment == null) {
            return null;
        }
        messageContentCipher.decryptMomentsCommentFields(comment);
        return comment;
    }

    private static MomentsComment cloneComment(MomentsComment source) {
        if (source == null) {
            return null;
        }
        return MomentsComment.builder()
                .id(source.getId())
                .postId(source.getPostId())
                .userId(source.getUserId())
                .content(source.getContent())
                .contentEncVersion(source.getContentEncVersion())
                .parentId(source.getParentId())
                .mentions(source.getMentions())
                .createTime(source.getCreateTime())
                .deleted(source.getDeleted())
                .build();
    }
}
