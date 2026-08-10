package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.dto.AdminHomepageSectionReorderDTO;
import com.linkx.server.controller.admin.vo.AdminHomepageSectionVO;
import com.linkx.server.controller.vo.AppHomepageVO;

import java.util.List;

public interface AdminHomepageSectionService {

    List<AdminHomepageSectionVO> listSections();

    void reorder(AdminHomepageSectionReorderDTO dto, Long operatorId);

    AppHomepageVO buildClientHomepage();
}
