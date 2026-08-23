package com.linkx.server.mapper.row;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteTypeCountRow {

    private String type;
    private Long count;
}
