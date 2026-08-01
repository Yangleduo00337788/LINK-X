package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminActivityDTO;
import com.linkx.server.controller.admin.dto.AdminActivityQueryDTO;
import com.linkx.server.controller.admin.vo.AdminActivityUploadVO;
import com.linkx.server.controller.admin.vo.AdminActivityVO;
import com.linkx.server.controller.vo.AppActivityVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminActivityService {

    PageResultVO<AdminActivityVO> list(AdminActivityQueryDTO query);

    AdminActivityVO detail(Long id);

    AdminActivityVO create(AdminActivityDTO dto, Long operatorId);

    AdminActivityVO update(Long id, AdminActivityDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminActivityVO publish(Long id, Long operatorId);

    AdminActivityVO unpublish(Long id, Long operatorId);

    AdminActivityUploadVO uploadImage(MultipartFile file, Long operatorId);

    List<AppActivityVO> listPublishedForClient();
}
