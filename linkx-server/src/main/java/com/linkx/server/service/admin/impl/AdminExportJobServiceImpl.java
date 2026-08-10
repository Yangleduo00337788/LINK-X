package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.admin.AdminExportModule;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminExportJobCreateDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminExportJobVO;
import com.linkx.server.entity.admin.SysAdminExportJob;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysAdminExportJobMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminExportJobRunner;
import com.linkx.server.service.admin.AdminExportJobService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminExportJobServiceImpl implements AdminExportJobService {

    private static final int EXPIRE_HOURS = 24;
    private static final int MAX_PENDING_PER_USER = 5;

    private final SysAdminExportJobMapper exportJobMapper;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;
    private final AdminExportJobRunner exportJobRunner;

    @Override
    public AdminExportJobVO create(AdminExportJobCreateDTO dto, Long requesterId) {
        if (requesterId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        AdminExportModule module = AdminExportModule.fromCode(dto.getModule());
        if (!rbacService.hasPermission(requesterId, module.getPermission())) {
            throw new CustomException(403, "无导出权限");
        }

        long pending = exportJobMapper.selectCountByQuery(QueryWrapper.create()
                .where(SysAdminExportJob::getRequesterId).eq(requesterId)
                .and(SysAdminExportJob::getStatus).in(
                        SysAdminExportJob.STATUS_PENDING,
                        SysAdminExportJob.STATUS_RUNNING));
        if (pending >= MAX_PENDING_PER_USER) {
            throw new CustomException(429, "导出任务过多，请稍后再试");
        }

        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.HOUR_OF_DAY, EXPIRE_HOURS);

        String queryJson = writeQueryJson(dto.getQuery());
        SysAdminExportJob job = SysAdminExportJob.builder()
                .requesterId(requesterId)
                .module(module.getCode())
                .queryJson(queryJson)
                .status(SysAdminExportJob.STATUS_PENDING)
                .expireAt(cal.getTime())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        exportJobMapper.insert(job);
        exportJobRunner.run(job.getId());
        return toVo(job);
    }

    @Override
    public AdminExportJobVO detail(Long id, Long requesterId) {
        return toVo(requireOwned(id, requesterId));
    }

    @Override
    public PageResultVO<AdminExportJobVO> list(AdminPageQueryDTO query, Long requesterId) {
        if (requesterId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 20 : Math.min(query.getSize(), 100);

        QueryWrapper qw = QueryWrapper.create()
                .where(SysAdminExportJob::getRequesterId).eq(requesterId);
        if (StringUtils.hasText(query.getKeyword())) {
            qw.and(SysAdminExportJob::getModule).like(query.getKeyword().trim());
        }
        long total = exportJobMapper.selectCountByQuery(qw);
        qw.orderBy(SysAdminExportJob::getCreateTime, false);
        qw.limit((page - 1L) * size, size);
        List<AdminExportJobVO> items = exportJobMapper.selectListByQuery(qw).stream()
                .map(this::toVo)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public SysAdminExportJob loadDownloadable(Long id, Long requesterId) {
        SysAdminExportJob job = requireOwned(id, requesterId);
        if (!SysAdminExportJob.STATUS_SUCCESS.equals(job.getStatus())) {
            throw new CustomException(400, "导出尚未完成或已失败");
        }
        if (job.getExpireAt() != null && job.getExpireAt().before(new Date())) {
            throw new CustomException(410, "导出文件已过期");
        }
        if (job.getContentBytes() == null || job.getContentBytes().length == 0) {
            throw new CustomException(404, "导出文件不存在");
        }
        return job;
    }

    @Override
    public void expireStaleJobs() {
        Date now = new Date();
        List<SysAdminExportJob> stale = exportJobMapper.selectListByQuery(QueryWrapper.create()
                .where(SysAdminExportJob::getExpireAt).lt(now)
                .and(SysAdminExportJob::getStatus).ne(SysAdminExportJob.STATUS_EXPIRED)
                .limit(200));
        for (SysAdminExportJob job : stale) {
            UpdateChain.of(SysAdminExportJob.class)
                    .set(SysAdminExportJob::getStatus, SysAdminExportJob.STATUS_EXPIRED)
                    .set(SysAdminExportJob::getContentBytes, (byte[]) null)
                    .set(SysAdminExportJob::getUpdateTime, now)
                    .where(SysAdminExportJob::getId).eq(job.getId())
                    .update();
        }
        if (!stale.isEmpty()) {
            log.info("清理过期导出任务 {} 条", stale.size());
        }
    }

    private SysAdminExportJob requireOwned(Long id, Long requesterId) {
        if (requesterId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        SysAdminExportJob job = exportJobMapper.selectOneById(id);
        if (job == null || (job.getDeleted() != null && job.getDeleted() == 1)) {
            throw new CustomException(404, "导出任务不存在");
        }
        if (!requesterId.equals(job.getRequesterId())) {
            throw new CustomException(403, "无权访问该导出任务");
        }
        return job;
    }

    private String writeQueryJson(Map<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            throw new CustomException(400, "invalid export query");
        }
    }

    private AdminExportJobVO toVo(SysAdminExportJob job) {
        return AdminExportJobVO.builder()
                .id(job.getId())
                .module(job.getModule())
                .status(job.getStatus())
                .rowCount(job.getRowCount())
                .fileName(job.getFileName())
                .errorMessage(job.getErrorMessage())
                .expireAt(job.getExpireAt())
                .createTime(job.getCreateTime())
                .updateTime(job.getUpdateTime())
                .build();
    }
}
