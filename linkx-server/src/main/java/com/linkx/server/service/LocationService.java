package com.linkx.server.service;

import com.linkx.server.controller.vo.LocationPlaceVO;

import java.util.List;

public interface LocationService {

    List<LocationPlaceVO> search(String keyword, int limit);
}
