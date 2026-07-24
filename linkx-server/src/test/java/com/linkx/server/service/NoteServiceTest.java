package com.linkx.server.service;

import com.linkx.server.controller.dto.SaveNoteDTO;
import com.linkx.server.controller.vo.NoteVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoteService tests")
class NoteServiceTest extends BaseIntegrationTest {

    @Autowired
    private NoteService noteService;

    @Nested
    @DisplayName("CRUD")
    class CrudTests {

        @Test
        @DisplayName("create then list contains note")
        void createAndList_success() {
            TestUser user = registerAndLogin("notesvc");
            SaveNoteDTO dto = new SaveNoteDTO();
            dto.setTitle("svc note");
            dto.setContent("body");
            NoteVO created = noteService.create(user.userId, dto);

            assertNotNull(created.getId());
            assertEquals("svc note", created.getTitle());

            List<NoteVO> notes = noteService.list(user.userId);
            assertTrue(notes.stream().anyMatch(n -> n.getId().equals(created.getId())));
        }

        @Test
        @DisplayName("update and delete success")
        void updateAndDelete_success() {
            TestUser user = registerAndLogin("notesvc2");
            SaveNoteDTO dto = new SaveNoteDTO();
            dto.setTitle("old");
            dto.setContent("old body");
            NoteVO created = noteService.create(user.userId, dto);

            SaveNoteDTO update = new SaveNoteDTO();
            update.setTitle("new");
            update.setContent("new body");
            NoteVO updated = noteService.update(user.userId, created.getId(), update);
            assertEquals("new", updated.getTitle());

            noteService.delete(user.userId, created.getId());
            assertThrows(CustomException.class, () -> noteService.get(user.userId, created.getId()));
        }

        @Test
        @DisplayName("blank media key throws")
        void resolveMediaUrl_blank() {
            TestUser user = registerAndLogin("notesvc3");
            assertThrows(CustomException.class, () -> noteService.resolveMediaUrl(user.userId, "  "));
        }

        @Test
        @DisplayName("foreign object key rejected")
        void resolveMediaUrl_foreignKey_forbidden() {
            TestUser owner = registerAndLogin("noteown");
            TestUser stranger = registerAndLogin("notestr");
            CustomException ex = assertThrows(CustomException.class,
                    () -> noteService.resolveMediaUrl(stranger.userId, "2026/07/24/not-owned.png"));
            assertEquals(403, ex.getCode());
            // owner also forbidden until claim / note content reference
            CustomException ex2 = assertThrows(CustomException.class,
                    () -> noteService.resolveMediaUrl(owner.userId, "2026/07/24/not-owned.png"));
            assertEquals(403, ex2.getCode());
        }
    }
}
