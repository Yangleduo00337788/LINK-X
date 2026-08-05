package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminApprovalActionDTO;
import com.linkx.server.controller.admin.dto.AdminApprovalStartDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalInboxItemVO;
import com.linkx.server.controller.admin.vo.AdminApprovalInstanceVO;
import com.linkx.server.entity.admin.SysApprovalInstance;
import com.linkx.server.entity.admin.SysApprovalRecord;
import com.linkx.server.mapper.admin.SysApprovalInstanceMapper;
import com.linkx.server.mapper.admin.SysApprovalRecordMapper;
import com.linkx.server.service.admin.AdminApprovalService;
import com.linkx.server.service.admin.ApprovalTempGrantService;
import com.linkx.server.service.admin.approval.ApprovalFlowEngine;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminApprovalServiceImpl implements AdminApprovalService {

    private final SysApprovalRecordMapper recordMapper;
    private final SysApprovalInstanceMapper instanceMapper;
    private final ApprovalFlowEngine approvalFlowEngine;
    private final ApprovalTempGrantService approvalTempGrantService;

    @Override
    public PageResultVO<AdminApprovalInboxItemVO> inbox(AdminPageQueryDTO query, Long operatorId) {
        approvalTempGrantService.syncGrantsForUser(operatorId);
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysApprovalRecord::getAssigneeId).eq(operatorId)
                .and(SysApprovalRecord::getStatus).eq(SysApprovalRecord.STATUS_PENDING)
                .and(SysApprovalRecord::getNodeType).ne(SysApprovalRecord.NODE_CC)
                .orderBy(SysApprovalRecord::getCreateTime, false);
        long total = recordMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<SysApprovalRecord> records = recordMapper.selectListByQuery(qw);
        Map<Long, SysApprovalInstance> instanceMap = loadInstances(records);
        List<AdminApprovalInboxItemVO> items = records.stream()
                .map(r -> toInboxItem(r, instanceMap.get(r.getInstanceId())))
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public PageResultVO<AdminApprovalInboxItemVO> ccInbox(AdminPageQueryDTO query, Long operatorId) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysApprovalRecord::getAssigneeId).eq(operatorId)
                .and(SysApprovalRecord::getNodeType).eq(SysApprovalRecord.NODE_CC)
                .orderBy(SysApprovalRecord::getActionTime, false)
                .orderBy(SysApprovalRecord::getCreateTime, false);
        long total = recordMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<SysApprovalRecord> records = recordMapper.selectListByQuery(qw);
        Map<Long, SysApprovalInstance> instanceMap = loadInstances(records);
        List<AdminApprovalInboxItemVO> items = records.stream()
                .map(r -> toInboxItem(r, instanceMap.get(r.getInstanceId())))
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminApprovalInstanceVO start(AdminApprovalStartDTO dto, Long operatorId) {
        return approvalFlowEngine.start(dto, operatorId);
    }

    @Override
    public AdminApprovalInstanceVO instanceDetail(Long instanceId, Long operatorId) {
        approvalTempGrantService.syncGrantsForUser(operatorId);
        return approvalFlowEngine.instanceDetail(instanceId);
    }

    @Override
    public void approve(Long recordId, AdminApprovalActionDTO dto, Long operatorId) {
        approvalTempGrantService.syncGrantsForUser(operatorId);
        approvalFlowEngine.approveRecord(recordId, dto, operatorId);
    }

    @Override
    public void reject(Long recordId, AdminApprovalActionDTO dto, Long operatorId) {
        approvalTempGrantService.syncGrantsForUser(operatorId);
        approvalFlowEngine.rejectRecord(recordId, dto, operatorId);
    }

    private Map<Long, SysApprovalInstance> loadInstances(List<SysApprovalRecord> records) {
        List<Long> ids = records.stream().map(SysApprovalRecord::getInstanceId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SysApprovalInstance> instances = instanceMapper.selectListByQuery(
                QueryWrapper.create().where(SysApprovalInstance::getId).in(ids));
        Map<Long, SysApprovalInstance> map = new HashMap<>();
        for (SysApprovalInstance instance : instances) {
            map.put(instance.getId(), instance);
        }
        return map;
    }

    private AdminApprovalInboxItemVO toInboxItem(SysApprovalRecord record, SysApprovalInstance instance) {
        AdminApprovalInboxItemVO.AdminApprovalInboxItemVOBuilder builder = AdminApprovalInboxItemVO.builder()
                .recordId(String.valueOf(record.getId()))
                .instanceId(String.valueOf(record.getInstanceId()))
                .stepName(record.getStepName())
                .nodeType(record.getNodeType())
                .status(record.getStatus())
                .createTime(record.getCreateTime());
        if (instance != null) {
            builder.title(instance.getTitle())
                    .flowName(instance.getFlowName())
                    .bizType(instance.getBizType())
                    .bizId(instance.getBizId())
                    .applicantName(instance.getApplicantName());
        }
        return builder.build();
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
