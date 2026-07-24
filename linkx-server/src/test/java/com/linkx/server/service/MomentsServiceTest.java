package com.linkx.server.service;

import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.dto.UpdateMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MomentsService tests")
class MomentsServiceTest extends BaseIntegrationTest {

    @Autowired
    private MomentsService momentsService;

    private MomentsPostVO publish(TestUser user, String content) {
        PublishMomentsDTO dto = new PublishMomentsDTO();
        dto.setContent(content);
        return momentsService.publish(user.userId, dto);
    }

    @Nested
    @DisplayName("publish / list / update")
    class PublishListUpdateTests {

        @Test
        @DisplayName("publish then search by keyword")
        void publishAndSearch() {
            TestUser user = registerAndLogin("mssrch");
            MomentsPostVO post = publish(user, "keyword LinkXMoments");

            List<MomentsPostVO> found = momentsService.list(user.userId, null, 20, "LinkXMoments");
            assertTrue(found.stream().anyMatch(p -> p.getId().equals(post.getId())));
        }

        @Test
        @DisplayName("beforeId pagination excludes newer")
        void listWithBeforeId() {
            TestUser user = registerAndLogin("mspage");
            MomentsPostVO older = publish(user, "older");
            MomentsPostVO newer = publish(user, "newer");

            List<MomentsPostVO> page = momentsService.list(user.userId, newer.getId(), 20, null);
            assertTrue(page.stream().anyMatch(p -> p.getId().equals(older.getId())));
            assertTrue(page.stream().noneMatch(p -> p.getId().equals(newer.getId())));
        }

        @Test
        @DisplayName("update content success")
        void updateContent() {
            TestUser user = registerAndLogin("msupd");
            MomentsPostVO post = publish(user, "old");

            UpdateMomentsDTO dto = new UpdateMomentsDTO();
            dto.setContent("new");
            MomentsPostVO updated = momentsService.update(user.userId, post.getId(), dto);
            assertEquals("new", updated.getContent());
        }
    }

    @Nested
    @DisplayName("interact")
    class InteractTests {

        @Test
        @DisplayName("nested comment fills replyToNickname")
        void nestedComment() {
            TestUser user = registerAndLogin("msnest");
            MomentsPostVO post = publish(user, "post");

            CommentMomentsDTO parentDto = new CommentMomentsDTO();
            parentDto.setContent("parent");
            MomentsCommentVO parent = momentsService.comment(user.userId, post.getId(), parentDto);

            CommentMomentsDTO replyDto = new CommentMomentsDTO();
            replyDto.setContent("child");
            replyDto.setParentId(parent.getId());
            MomentsCommentVO reply = momentsService.comment(user.userId, post.getId(), replyDto);

            assertEquals(parent.getId(), reply.getParentId());
            assertEquals(parent.getNickname(), reply.getReplyToNickname());
        }

        @Test
        @DisplayName("like sets liked true")
        void like() {
            TestUser user = registerAndLogin("mslike");
            MomentsPostVO post = publish(user, "like");
            momentsService.like(user.userId, post.getId());

            List<MomentsPostVO> list = momentsService.list(user.userId, null, 20, null);
            MomentsPostVO found = list.stream()
                    .filter(p -> p.getId().equals(post.getId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(found.isLiked());
        }

        @Test
        @DisplayName("friends-only post hidden from stranger list/like")
        void friendsOnlyHiddenFromStranger() {
            TestUser author = registerAndLogin("msvisA");
            TestUser stranger = registerAndLogin("msvisB");

            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("friends only");
            dto.setVisibility(1);
            MomentsPostVO post = momentsService.publish(author.userId, dto);

            List<MomentsPostVO> strangerView = momentsService.listByUser(
                    stranger.userId, author.userId, null, 20, null);
            assertTrue(strangerView.stream().noneMatch(p -> p.getId().equals(post.getId())));

            CustomException ex = assertThrows(CustomException.class,
                    () -> momentsService.like(stranger.userId, post.getId()));
            assertEquals(403, ex.getCode());
        }
    }

    @Nested
    @DisplayName("upload validation")
    class UploadTests {

        @Test
        @DisplayName("invalid type throws 400")
        void upload_invalidType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.txt", "text/plain", "x".getBytes());
            CustomException ex = assertThrows(CustomException.class,
                    () -> momentsService.uploadImage(1L, file));
            assertEquals(400, ex.getCode());
        }
    }
}
