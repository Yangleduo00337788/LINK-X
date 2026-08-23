package com.linkx.server.mapper.row;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderChildCountRow {

    private Long parentId;
    private Long count;
}
