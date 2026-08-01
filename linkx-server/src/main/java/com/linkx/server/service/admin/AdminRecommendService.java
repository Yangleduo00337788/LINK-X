package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminRecommendDTO;
import com.linkx.server.controller.admin.dto.AdminRecommendQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRecommendUploadVO;
import com.linkx.server.controller.admin.vo.AdminRecommendVO;
import com.linkx.server.controller.vo.AppRecommendVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminRecommendService {

    PageResultVO<AdminRecommendVO> list(AdminRecommendQueryDTO query);

    AdminRecommendVO detail(Long id);

    AdminRecommendVO create(AdminRecommendDTO dto, Long operatorId);

    AdminRecommendVO update(Long id, AdminRecommendDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminRecommendVO publish(Long id, Long operatorId);

    AdminRecommendVO unpublish(Long id, Long operatorId);

    AdminRecommendUploadVO uploadImage(MultipartFile file, Long operatorId);

    List<AppRecommendVO> listPublishedForClient(String slotCode);
}
