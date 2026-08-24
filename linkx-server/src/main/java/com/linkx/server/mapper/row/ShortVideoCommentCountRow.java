package com.linkx.server.mapper.row;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortVideoCommentCountRow {

    private Long postId;
    private Long count;
}
