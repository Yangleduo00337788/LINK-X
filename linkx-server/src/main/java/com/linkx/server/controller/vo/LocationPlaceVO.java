package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationPlaceVO {
    private String name;
    private String address;
    private Double lat;
    private Double lon;
}
