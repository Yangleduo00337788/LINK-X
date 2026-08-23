package com.linkx.server.mapper;


import com.linkx.server.mapper.row.FavoriteTypeCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FavoriteSqlMapper {

    @Select("""
            SELECT type, COUNT(*) AS count
            FROM favorite
            WHERE user_id = #{userId} AND deleted = 0
            GROUP BY type
            """)
    List<FavoriteTypeCountRow> countByType(@Param("userId") Long userId);

    @Select("""
            SELECT tags
            FROM favorite
            WHERE user_id = #{userId} AND deleted = 0 AND tags IS NOT NULL AND tags <> ''
            """)
    List<String> selectTagColumnsByUser(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM favorite
            WHERE user_id = #{userId} AND deleted = 0
            """)
    long countByUser(@Param("userId") Long userId);
}
