package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminBannerDTO;
import com.linkx.server.controller.admin.dto.AdminBannerQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBannerUploadVO;
import com.linkx.server.controller.admin.vo.AdminBannerVO;
import com.linkx.server.controller.vo.AppBannerVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminBannerService {

    PageResultVO<AdminBannerVO> list(AdminBannerQueryDTO query);

    AdminBannerVO detail(Long id);

    AdminBannerVO create(AdminBannerDTO dto, Long operatorId);

    AdminBannerVO update(Long id, AdminBannerDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminBannerVO publish(Long id, Long operatorId);

    AdminBannerVO unpublish(Long id, Long operatorId);

    AdminBannerUploadVO uploadImage(MultipartFile file, Long operatorId);

    /** 客户端可见：已发布且在时效窗内 */
    List<AppBannerVO> listPublishedForClient(String position);
}
