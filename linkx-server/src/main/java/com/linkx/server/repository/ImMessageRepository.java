package com.linkx.server.repository;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.ImMessage;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IM 消息持久化入口：写入前加密、读出后解密，业务层始终处理明文。
 */
@Repository
@RequiredArgsConstructor
public class ImMessageRepository {

    private final ImMessageMapper messageMapper;
    private final MessageContentCipher messageContentCipher;

    public void insert(ImMessage message) {
        ImMessage stored = copyForPersistence(message);
        messageMapper.insert(stored);
        message.setId(stored.getId());
    }

    public void update(ImMessage message) {
        messageMapper.update(copyForPersistence(message));
    }

    public void updateByQuery(ImMessage patch, QueryWrapper query) {
        messageMapper.updateByQuery(copyForPersistence(patch), query);
    }

    public ImMessage selectOneById(Long id) {
        return decrypt(messageMapper.selectOneById(id));
    }

    public ImMessage selectOneByQuery(QueryWrapper query) {
        return decrypt(messageMapper.selectOneByQuery(query));
    }

    public List<ImMessage> selectListByQuery(QueryWrapper query) {
        List<ImMessage> rows = messageMapper.selectListByQuery(query);
        messageContentCipher.decryptMessageFields(rows);
        return rows;
    }

    /** 不解密正文，适用于仅依赖元数据/附件字段的批量任务。 */
    public List<ImMessage> selectListByQueryWithoutDecrypt(QueryWrapper query) {
        return messageMapper.selectListByQuery(query);
    }

    public long selectCountByQuery(QueryWrapper query) {
        return messageMapper.selectCountByQuery(query);
    }

    /**
     * 统计仍待重加密的消息条数（content 或 quote_content 任一为明文）。
     */
    public long countPendingReencrypt() {
        return messageMapper.selectCountByQuery(pendingReencryptQuery());
    }

    /**
     * 批量将历史明文 content / quote_content 加密落库。
     *
     * @return 本批实际更新的行数
     */
    public int reencryptPlaintextBatch(int batchSize) {
        if (!messageContentCipher.isEnabled() || batchSize <= 0) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 1), 5000);
        List<ImMessage> rows = messageMapper.selectListByQuery(
                pendingReencryptQuery().orderBy(ImMessage::getId, true).limit(limit));
        if (rows.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (ImMessage row : rows) {
            if (reencryptRow(row)) {
                updated++;
            }
        }
        return updated;
    }

    private boolean reencryptRow(ImMessage row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        UpdateChain<ImMessage> chain = UpdateChain.of(ImMessage.class);
        boolean changed = false;

        if (messageContentCipher.needsContentReencrypt(row.getContent(), row.getContentEncVersion())) {
            chain.set(ImMessage::getContent,
                    messageContentCipher.encryptPlaintextForStorage(row.getContent()));
            chain.set(ImMessage::getContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        } else if (messageContentCipher.isEncryptedContent(row.getContent(), row.getContentEncVersion())
                && (row.getContentEncVersion() == null || row.getContentEncVersion() == 0)) {
            chain.set(ImMessage::getContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (messageContentCipher.needsQuoteReencrypt(row.getQuoteContent(), row.getQuoteContentEncVersion())) {
            chain.set(ImMessage::getQuoteContent,
                    messageContentCipher.encryptPlaintextForStorage(row.getQuoteContent()));
            chain.set(ImMessage::getQuoteContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        } else if (messageContentCipher.isEncryptedContent(row.getQuoteContent(), row.getQuoteContentEncVersion())
                && (row.getQuoteContentEncVersion() == null || row.getQuoteContentEncVersion() == 0)) {
            chain.set(ImMessage::getQuoteContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (!changed) {
            return false;
        }
        chain.where(ImMessage::getId).eq(row.getId()).update();
        return true;
    }

    /**
     * 统计仍使用非当前 keyId 加密的密文条数。
     */
    public long countPendingKeyRotation() {
        if (!messageContentCipher.isEnabled()) {
            return 0;
        }
        return messageMapper.selectCountByQuery(pendingKeyRotationQuery());
    }

    /**
     * 批量将旧 keyId 密文轮换为当前 keyId。
     */
    public int rotateEncryptedKeyBatch(int batchSize) {
        if (!messageContentCipher.isEnabled() || batchSize <= 0) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 1), 5000);
        List<ImMessage> rows = messageMapper.selectListByQuery(
                pendingKeyRotationQuery().orderBy(ImMessage::getId, true).limit(limit));
        if (rows.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (ImMessage row : rows) {
            if (rotateKeyRow(row)) {
                updated++;
            }
        }
        return updated;
    }

    private boolean rotateKeyRow(ImMessage row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        UpdateChain<ImMessage> chain = UpdateChain.of(ImMessage.class);
        boolean changed = false;

        if (messageContentCipher.needsKeyRotation(row.getContent())) {
            chain.set(ImMessage::getContent,
                    messageContentCipher.rotateCiphertextForStorage(row.getContent()));
            chain.set(ImMessage::getContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (messageContentCipher.needsKeyRotation(row.getQuoteContent())) {
            chain.set(ImMessage::getQuoteContent,
                    messageContentCipher.rotateCiphertextForStorage(row.getQuoteContent()));
            chain.set(ImMessage::getQuoteContentEncVersion, MessageContentCipher.ENC_VERSION);
            changed = true;
        }

        if (!changed) {
            return false;
        }
        chain.where(ImMessage::getId).eq(row.getId()).update();
        return true;
    }

    private QueryWrapper pendingKeyRotationQuery() {
        String currentPrefix = messageContentCipher.currentKeyPrefix();
        return QueryWrapper.create().where(
                "((content_enc_version = 1 AND content LIKE 'lxenc:v1:%' AND content NOT LIKE ?) "
                        + "OR (quote_content_enc_version = 1 AND quote_content LIKE 'lxenc:v1:%' AND quote_content NOT LIKE ?))",
                currentPrefix + "%",
                currentPrefix + "%");
    }

    private static QueryWrapper pendingReencryptQuery() {
        return QueryWrapper.create().where(
                "((content_enc_version = 0 AND content IS NOT NULL AND TRIM(content) <> '') "
                        + "OR (quote_content_enc_version = 0 AND quote_content IS NOT NULL AND TRIM(quote_content) <> ''))");
    }

    private ImMessage copyForPersistence(ImMessage source) {
        ImMessage copy = cloneMessage(source);
        messageContentCipher.encryptMessageFields(copy);
        return copy;
    }

    private ImMessage decrypt(ImMessage message) {
        if (message == null) {
            return null;
        }
        messageContentCipher.decryptMessageFields(message);
        return message;
    }

    private static ImMessage cloneMessage(ImMessage source) {
        if (source == null) {
            return null;
        }
        return ImMessage.builder()
                .id(source.getId())
                .conversationId(source.getConversationId())
                .senderId(source.getSenderId())
                .type(source.getType())
                .content(source.getContent())
                .contentEncVersion(source.getContentEncVersion())
                .fileName(source.getFileName())
                .fileSize(source.getFileSize())
                .fileUrl(source.getFileUrl())
                .clientMsgId(source.getClientMsgId())
                .deliveryStatus(source.getDeliveryStatus())
                .readStatus(source.getReadStatus())
                .voiceDuration(source.getVoiceDuration())
                .edited(source.getEdited())
                .editedTime(source.getEditedTime())
                .forwardFromMessageId(source.getForwardFromMessageId())
                .forwardFromConversationId(source.getForwardFromConversationId())
                .quoteMessageId(source.getQuoteMessageId())
                .quoteConversationId(source.getQuoteConversationId())
                .quoteSenderId(source.getQuoteSenderId())
                .quoteContent(source.getQuoteContent())
                .quoteContentEncVersion(source.getQuoteContentEncVersion())
                .quoteType(source.getQuoteType())
                .createTime(source.getCreateTime())
                .deleted(source.getDeleted())
                .build();
    }
}
