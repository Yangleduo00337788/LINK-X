package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.vo.LocationPlaceVO;

import java.util.List;

public interface LocationService {

    List<LocationPlaceVO> search(String keyword, int limit);
}
