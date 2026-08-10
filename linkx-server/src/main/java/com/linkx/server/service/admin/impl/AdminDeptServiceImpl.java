package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.dto.AdminDeptDTO;
import com.linkx.server.controller.admin.vo.AdminDeptVO;
import com.linkx.server.entity.SysDept;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeptMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.admin.AdminDeptService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminDeptServiceImpl implements AdminDeptService {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<AdminDeptVO> tree() {
        List<SysDept> rows = sysDeptMapper.selectListByQuery(
                QueryWrapper.create().orderBy(SysDept::getSortOrder, true).orderBy(SysDept::getId, true));
        return buildTree(rows);
    }

    @Override
    public AdminDeptVO detail(Long id) {
        return toVO(requireDept(id), null);
    }

    @Override
    @Transactional
    public Long create(AdminDeptDTO dto, Long operatorId) {
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            requireDept(parentId);
        }
        SysDept dept = SysDept.builder()
                .parentId(parentId)
                .name(dto.getName().trim())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .createBy(operatorId)
                .updateBy(operatorId)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        sysDeptMapper.insert(dept);
        return dept.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminDeptDTO dto, Long operatorId) {
        SysDept dept = requireDept(id);
        Long parentId = dto.getParentId() == null ? dept.getParentId() : dto.getParentId();
        if (parentId == null) {
            parentId = 0L;
        }
        if (Objects.equals(parentId, id)) {
            throw new CustomException(400, "父部门不能是自己");
        }
        if (parentId != 0L) {
            requireDept(parentId);
            if (isDescendant(id, parentId)) {
                throw new CustomException(400, "父部门不能是自己的下级");
            }
        }
        if (StringUtils.hasText(dto.getName())) {
            dept.setName(dto.getName().trim());
        }
        dept.setParentId(parentId);
        if (dto.getSortOrder() != null) {
            dept.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            dept.setStatus(dto.getStatus());
        }
        dept.setUpdateBy(operatorId);
        dept.setUpdateTime(new Date());
        sysDeptMapper.update(dept);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireDept(id);
        long children = sysDeptMapper.selectCountByQuery(
                QueryWrapper.create().where(SysDept::getParentId).eq(id));
        if (children > 0) {
            throw new CustomException(400, "请先删除子部门");
        }
        long users = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getDeptId).eq(id));
        if (users > 0) {
            throw new CustomException(400, "部门下仍有用户，无法删除");
        }
        sysDeptMapper.deleteById(id);
    }

    private boolean isDescendant(Long rootId, Long candidateId) {
        Long current = candidateId;
        int guard = 0;
        while (current != null && current != 0L && guard++ < 64) {
            if (Objects.equals(current, rootId)) {
                return true;
            }
            SysDept parent = sysDeptMapper.selectOneById(current);
            current = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    private List<AdminDeptVO> buildTree(List<SysDept> rows) {
        Map<Long, AdminDeptVO> map = new HashMap<>();
        for (SysDept row : rows) {
            map.put(row.getId(), toVO(row, new ArrayList<>()));
        }
        List<AdminDeptVO> roots = new ArrayList<>();
        for (SysDept row : rows) {
            AdminDeptVO node = map.get(row.getId());
            Long parentId = row.getParentId() == null ? 0L : row.getParentId();
            if (parentId == 0L || !map.containsKey(parentId)) {
                roots.add(node);
            } else {
                map.get(parentId).getChildren().add(node);
            }
        }
        sortRecursively(roots);
        return roots;
    }

    private void sortRecursively(List<AdminDeptVO> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort(Comparator
                .comparing((AdminDeptVO n) -> n.getSortOrder() == null ? 0 : n.getSortOrder())
                .thenComparing(n -> n.getId() == null ? 0L : n.getId()));
        for (AdminDeptVO n : nodes) {
            sortRecursively(n.getChildren());
        }
    }

    private AdminDeptVO toVO(SysDept dept, List<AdminDeptVO> children) {
        return AdminDeptVO.builder()
                .id(dept.getId())
                .parentId(dept.getParentId())
                .name(dept.getName())
                .sortOrder(dept.getSortOrder())
                .status(dept.getStatus())
                .createTime(dept.getCreateTime())
                .updateTime(dept.getUpdateTime())
                .children(children)
                .build();
    }

    private SysDept requireDept(Long id) {
        SysDept dept = sysDeptMapper.selectOneById(id);
        if (dept == null) {
            throw new CustomException(404, "部门不存在");
        }
        return dept;
    }
}
