package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminVersionDTO;
import com.linkx.server.controller.admin.dto.AdminVersionQueryDTO;
import com.linkx.server.controller.admin.dto.AdminVersionMultipartCompleteDTO;
import com.linkx.server.controller.admin.dto.AdminVersionMultipartInitDTO;
import com.linkx.server.controller.admin.vo.AdminVersionMultipartInitVO;
import com.linkx.server.controller.admin.vo.AdminVersionUploadVO;
import com.linkx.server.controller.admin.vo.AdminVersionVO;
import org.springframework.web.multipart.MultipartFile;

public interface AdminVersionService {

    PageResultVO<AdminVersionVO> list(AdminVersionQueryDTO query);

    AdminVersionVO detail(Long id);

    AdminVersionVO create(AdminVersionDTO dto, Long operatorId);

    AdminVersionVO update(Long id, AdminVersionDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminVersionVO publish(Long id, Long operatorId);

    AdminVersionUploadVO uploadPackage(MultipartFile file, Long operatorId);

    AdminVersionMultipartInitVO initInstallerMultipart(AdminVersionMultipartInitDTO dto, Long operatorId);

    void uploadInstallerPart(MultipartFile file, String uploadId, String objectKey, int partNumber, Long operatorId);

    AdminVersionUploadVO completeInstallerMultipart(AdminVersionMultipartCompleteDTO dto, Long operatorId);
}
