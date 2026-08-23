package com.linkx.server.mapper;


import com.linkx.server.mapper.row.FolderChildCountRow;
import com.linkx.server.mapper.row.FolderFileAggRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CloudDriveSqlMapper {

    @Select("""
            SELECT COUNT(*)
            FROM cloud_folder child
            INNER JOIN cloud_folder ancestor
                ON ancestor.id = #{ancestorId}
               AND ancestor.user_id = #{userId}
               AND ancestor.deleted = 0
            WHERE child.id = #{childId}
              AND child.user_id = #{userId}
              AND child.deleted = 0
              AND (child.path = ancestor.path OR child.path LIKE CONCAT(ancestor.path, '/%'))
            """)
    int countDescendant(@Param("userId") Long userId,
                        @Param("ancestorId") Long ancestorId,
                        @Param("childId") Long childId);

    @Select("""
            WITH RECURSIVE subtree AS (
                SELECT id FROM cloud_folder
                WHERE id = #{folderId} AND user_id = #{userId} AND deleted = 0
                UNION ALL
                SELECT cf.id FROM cloud_folder cf
                INNER JOIN subtree s ON cf.parent_id = s.id
                WHERE cf.user_id = #{userId} AND cf.deleted = 0
            )
            SELECT id FROM subtree
            """)
    List<Long> selectSubtreeFolderIds(@Param("userId") Long userId, @Param("folderId") Long folderId);

    @Select("""
            SELECT parent_id AS parentId, COUNT(*) AS count
            FROM cloud_folder
            WHERE user_id = #{userId} AND deleted = 0 AND parent_id IN (${parentIds})
            GROUP BY parent_id
            """)
    List<FolderChildCountRow> countDirectSubfolders(@Param("userId") Long userId,
                                                   @Param("parentIds") String parentIds);

    @Select("""
            SELECT folder_id AS parentId, COUNT(*) AS count
            FROM cloud_file
            WHERE user_id = #{userId} AND deleted = 0 AND folder_id IN (${folderIds})
            GROUP BY folder_id
            """)
    List<FolderChildCountRow> countDirectFiles(@Param("userId") Long userId,
                                             @Param("folderIds") String folderIds);

    @Select("""
            SELECT COALESCE(SUM(cf.file_size), 0) AS totalSize, COUNT(*) AS fileCount
            FROM cloud_file cf
            INNER JOIN cloud_folder f
                ON f.id = cf.folder_id
               AND f.user_id = #{userId}
               AND f.deleted = 0
            WHERE cf.user_id = #{userId}
              AND cf.deleted = 0
              AND (f.path = #{basePath} OR f.path LIKE CONCAT(#{basePath}, '/%'))
            """)
    FolderFileAggRow aggregateSubtreeFiles(@Param("userId") Long userId, @Param("basePath") String basePath);
}
