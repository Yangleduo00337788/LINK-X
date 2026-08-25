package com.linkx.server.entity;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("short_video_post_topic")
public class ShortVideoPostTopic implements Serializable {

    private Long postId;

    private Long topicId;
}
