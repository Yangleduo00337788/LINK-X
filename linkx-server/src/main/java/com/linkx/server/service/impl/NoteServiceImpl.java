package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SaveNoteDTO;
import com.linkx.server.controller.vo.NoteFileUploadVO;
import com.linkx.server.controller.vo.NoteVO;
import com.linkx.server.entity.Note;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.NoteMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.NoteService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 笔记服务实现
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NoteMapper noteMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;

    @Override
    public List<NoteVO> list(Long userId) {
        List<Note> notes = noteMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .orderBy("update_time", false)
        );
        return notes.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public NoteVO get(Long userId, Long noteId) {
        Note note = noteMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", noteId)
                        .eq("deleted", 0)
        );
        if (note == null) {
            throw new CustomException(404, "笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new CustomException(403, "无权访问此笔记");
        }
        return toVO(note);
    }

    @Override
    @Transactional
    public NoteVO create(Long userId, SaveNoteDTO dto) {
        Note note = Note.builder()
                .userId(userId)
                .title(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "无标题笔记")
                .content(dto.getContent())
                .type(normalizeType(dto.getType()))
                .build();
        noteMapper.insert(note);
        return toVO(note);
    }

    @Override
    @Transactional
    public NoteVO update(Long userId, Long noteId, SaveNoteDTO dto) {
        Note note = noteMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", noteId)
                        .eq("deleted", 0)
        );
        if (note == null) {
            throw new CustomException(404, "笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new CustomException(403, "无权修改此笔记");
        }

        if (StringUtils.hasText(dto.getTitle())) {
            note.setTitle(dto.getTitle());
        }
        note.setContent(dto.getContent());
        if (StringUtils.hasText(dto.getType())) {
            note.setType(normalizeType(dto.getType()));
        }
        noteMapper.update(note);
        return toVO(note);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long noteId) {
        Note note = noteMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", noteId)
                        .eq("deleted", 0)
        );
        if (note == null) {
            throw new CustomException(404, "笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new CustomException(403, "无权删除此笔记");
        }
        noteMapper.deleteById(noteId);
    }

    @Override
    public NoteFileUploadVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "文件不能为空");
        }
        try {
            String fileKey = fileStorageService.uploadFile(file);
            String url = mediaUrlService.resolve(fileKey);
            return NoteFileUploadVO.builder()
                    .fileKey(fileKey)
                    .url(url)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(500, "文件上传失败");
        }
    }

    @Override
    public String resolveMediaUrl(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            throw new CustomException(400, "媒体 key 不能为空");
        }
        String key = fileKey.trim();
        if (key.startsWith("lx-media:")) {
            key = key.substring("lx-media:".length());
        }
        String url = mediaUrlService.resolve(key);
        if (!StringUtils.hasText(url)) {
            throw new CustomException(404, "媒体不存在或无法访问");
        }
        return url;
    }

    private NoteVO toVO(Note note) {
        return NoteVO.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .type(note.getType() != null ? note.getType() : "note")
                .createTime(formatTime(note.getCreateTime()))
                .updateTime(formatTime(note.getUpdateTime()))
                .build();
    }

    private String normalizeType(String raw) {
        // 笔记域仅保留 note；历史 image/link/file 请迁到 /favorites
        return "note";
    }

    private String formatTime(Date date) {
        if (date == null) return "";
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_FORMATTER);
    }
}
