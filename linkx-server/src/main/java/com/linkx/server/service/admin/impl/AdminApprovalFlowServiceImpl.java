package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminApprovalFlowDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalFlowVO;
import com.linkx.server.entity.admin.SysApprovalFlow;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysApprovalFlowMapper;
import com.linkx.server.service.admin.AdminApprovalFlowService;
import com.linkx.server.service.admin.approval.ApprovalFlowEngine;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminApprovalFlowServiceImpl implements AdminApprovalFlowService {

    private final SysApprovalFlowMapper flowMapper;
    private final ApprovalFlowEngine approvalFlowEngine;

    @Override
    public PageResultVO<AdminApprovalFlowVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create().where(SysApprovalFlow::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(SysApprovalFlow::getName).like(kw)
                    .or(SysApprovalFlow::getBizType).like(kw);
        }
        if (query.getStatus() != null) {
            qw.and(SysApprovalFlow::getEnabled).eq(query.getStatus() == 1);
        }
        qw.orderBy(SysApprovalFlow::getPriority, false)
                .orderBy(SysApprovalFlow::getUpdateTime, false);
        long total = flowMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminApprovalFlowVO> items = flowMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminApprovalFlowVO detail(Long id) {
        return toVO(requireFlow(id));
    }

    @Override
    @Transactional
    public AdminApprovalFlowVO create(AdminApprovalFlowDTO dto, Long operatorId) {
        validateSteps(dto.getStepsJson());
        Date now = new Date();
        SysApprovalFlow entity = fromDto(dto);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        flowMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminApprovalFlowVO update(Long id, AdminApprovalFlowDTO dto, Long operatorId) {
        validateSteps(dto.getStepsJson());
        SysApprovalFlow entity = requireFlow(id);
        applyDto(entity, dto);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        flowMapper.update(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysApprovalFlow entity = requireFlow(id);
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        flowMapper.update(entity);
    }

    private void validateSteps(String stepsJson) {
        if (approvalFlowEngine.parseSteps(stepsJson).isEmpty()) {
            throw new CustomException(400, "steps required");
        }
    }

    private SysApprovalFlow requireFlow(Long id) {
        SysApprovalFlow flow = flowMapper.selectOneById(id);
        if (flow == null || flow.getDeleted() != null && flow.getDeleted() == 1) {
            throw new CustomException(404, "approval flow not found");
        }
        return flow;
    }

    private static void applyDto(SysApprovalFlow entity, AdminApprovalFlowDTO dto) {
        entity.setName(dto.getName().trim());
        entity.setBizType(dto.getBizType().trim().toLowerCase(Locale.ROOT));
        entity.setDescription(dto.getDescription());
        entity.setStepsJson(dto.getStepsJson());
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        entity.setAutoStart(Boolean.TRUE.equals(dto.getAutoStart()));
        entity.setPriority(dto.getPriority() == null ? 0 : dto.getPriority());
    }

    private static SysApprovalFlow fromDto(AdminApprovalFlowDTO dto) {
        SysApprovalFlow entity = new SysApprovalFlow();
        applyDto(entity, dto);
        return entity;
    }

    private AdminApprovalFlowVO toVO(SysApprovalFlow entity) {
        return AdminApprovalFlowVO.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .bizType(entity.getBizType())
                .description(entity.getDescription())
                .stepsJson(entity.getStepsJson())
                .enabled(entity.getEnabled())
                .autoStart(entity.getAutoStart())
                .priority(entity.getPriority())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
