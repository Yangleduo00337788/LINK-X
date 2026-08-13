package com.linkx.server.repository;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.MomentsPost;
import com.linkx.server.mapper.MomentsPostMapper;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 朋友圈动态持久化：写入前加密、读出后解密，业务层始终处理明文。
 */
@Repository
@RequiredArgsConstructor
public class MomentsPostRepository {

    private final MomentsPostMapper postMapper;
    private final MessageContentCipher messageContentCipher;

    public void insert(MomentsPost post) {
        MomentsPost stored = copyForPersistence(post);
        postMapper.insert(stored);
        post.setId(stored.getId());
    }

    public void update(MomentsPost post) {
        postMapper.update(copyForPersistence(post));
    }

    public MomentsPost selectOneById(Long id) {
        return decrypt(postMapper.selectOneById(id));
    }

    public MomentsPost selectOneByQuery(QueryWrapper query) {
        return decrypt(postMapper.selectOneByQuery(query));
    }

    public List<MomentsPost> selectListByQuery(QueryWrapper query) {
        List<MomentsPost> rows = postMapper.selectListByQuery(query);
        messageContentCipher.decryptMomentsPostFields(rows);
        return rows;
    }

    public List<MomentsPost> selectListByQueryWithoutDecrypt(QueryWrapper query) {
        return postMapper.selectListByQuery(query);
    }

    public long selectCountByQuery(QueryWrapper query) {
        return postMapper.selectCountByQuery(query);
    }

    public long countPendingReencrypt() {
        return postMapper.selectCountByQuery(pendingReencryptQuery());
    }

    public int reencryptPlaintextBatch(int batchSize) {
        if (!messageContentCipher.isEnabled() || batchSize <= 0) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 1), 5000);
        List<MomentsPost> rows = postMapper.selectListByQuery(
                pendingReencryptQuery().orderBy(MomentsPost::getId, true).limit(limit));
        int updated = 0;
        for (MomentsPost row : rows) {
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
        return postMapper.selectCountByQuery(pendingKeyRotationQuery());
    }

    public int rotateEncryptedKeyBatch(int batchSize) {
        if (!messageContentCipher.isEnabled() || batchSize <= 0) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 1), 5000);
        List<MomentsPost> rows = postMapper.selectListByQuery(
                pendingKeyRotationQuery().orderBy(MomentsPost::getId, true).limit(limit));
        int updated = 0;
        for (MomentsPost row : rows) {
            if (rotateKeyRow(row)) {
                updated++;
            }
        }
        return updated;
    }

    private boolean reencryptRow(MomentsPost row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        UpdateChain<MomentsPost> chain = UpdateChain.of(MomentsPost.class);
        boolean changed = false;

        if (messageContentCipher.needsContentReencrypt(row.getContent(), row.getContentEncVersion())) {
            chain.set(MomentsPost::getContent,
                    messageContentCipher.encryptPlaintextForStorage(row.getContent()));
            chain.set(MomentsPost::getContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        } else if (messageContentCipher.isEncryptedContent(row.getContent(), row.getContentEncVersion())
                && (row.getContentEncVersion() == null || row.getContentEncVersion() == 0)) {
            chain.set(MomentsPost::getContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (messageContentCipher.needsLocationReencrypt(row.getLocation(), row.getLocationEncVersion())) {
            chain.set(MomentsPost::getLocation,
                    messageContentCipher.encryptPlaintextForStorage(row.getLocation()));
            chain.set(MomentsPost::getLocationEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        } else if (messageContentCipher.isEncryptedContent(row.getLocation(), row.getLocationEncVersion())
                && (row.getLocationEncVersion() == null || row.getLocationEncVersion() == 0)) {
            chain.set(MomentsPost::getLocationEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (!changed) {
            return false;
        }
        chain.where(MomentsPost::getId).eq(row.getId()).update();
        return true;
    }

    private boolean rotateKeyRow(MomentsPost row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        UpdateChain<MomentsPost> chain = UpdateChain.of(MomentsPost.class);
        boolean changed = false;

        if (messageContentCipher.needsKeyRotation(row.getContent())) {
            chain.set(MomentsPost::getContent,
                    messageContentCipher.rotateCiphertextForStorage(row.getContent()));
            chain.set(MomentsPost::getContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }
        if (messageContentCipher.needsKeyRotation(row.getLocation())) {
            chain.set(MomentsPost::getLocation,
                    messageContentCipher.rotateCiphertextForStorage(row.getLocation()));
            chain.set(MomentsPost::getLocationEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (!changed) {
            return false;
        }
        chain.where(MomentsPost::getId).eq(row.getId()).update();
        return true;
    }

    private QueryWrapper pendingKeyRotationQuery() {
        String currentPrefix = messageContentCipher.currentKeyPrefix();
        return QueryWrapper.create().where(
                "((content_enc_version = 1 AND content LIKE 'lxenc:v1:%' AND content NOT LIKE ?) "
                        + "OR (location_enc_version = 1 AND location LIKE 'lxenc:v1:%' AND location NOT LIKE ?))",
                currentPrefix + "%",
                currentPrefix + "%");
    }

    private static QueryWrapper pendingReencryptQuery() {
        return QueryWrapper.create().where(
                "((content_enc_version = 0 AND content IS NOT NULL AND TRIM(content) <> '') "
                        + "OR (location_enc_version = 0 AND location IS NOT NULL AND TRIM(location) <> ''))");
    }

    private MomentsPost copyForPersistence(MomentsPost source) {
        MomentsPost copy = clonePost(source);
        messageContentCipher.encryptMomentsPostFields(copy);
        return copy;
    }

    private MomentsPost decrypt(MomentsPost post) {
        if (post == null) {
            return null;
        }
        messageContentCipher.decryptMomentsPostFields(post);
        return post;
    }

    private static MomentsPost clonePost(MomentsPost source) {
        if (source == null) {
            return null;
        }
        return MomentsPost.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .content(source.getContent())
                .contentEncVersion(source.getContentEncVersion())
                .location(source.getLocation())
                .locationEncVersion(source.getLocationEncVersion())
                .atUsers(source.getAtUsers())
                .visibility(source.getVisibility())
                .createTime(source.getCreateTime())
                .deleted(source.getDeleted())
                .build();
    }
}
