package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminDeptDTO;
import com.linkx.server.controller.admin.vo.AdminDeptVO;
import com.linkx.server.entity.SysDept;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeptMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.admin.impl.AdminDeptServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminDeptService 部门管理")
class AdminDeptServiceTest {

    @Mock SysDeptMapper sysDeptMapper;
    @Mock SysUserMapper sysUserMapper;

    private AdminDeptServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDeptServiceImpl(sysDeptMapper, sysUserMapper);
    }

    private SysDept dept(long id, long parentId, String name) {
        return SysDept.builder()
                .id(id).parentId(parentId).name(name).sortOrder(0).status(1)
                .createTime(new Date()).updateTime(new Date()).build();
    }

    @Nested
    @DisplayName("查询")
    class Query {
        @Test
        @DisplayName("tree 构建父子")
        void tree() {
            when(sysDeptMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    dept(1L, 0L, "Root"),
                    dept(2L, 1L, "Child")
            ));
            List<AdminDeptVO> tree = service.tree();
            assertEquals(1, tree.size());
            assertEquals(1, tree.get(0).getChildren().size());
            assertEquals("Child", tree.get(0).getChildren().get(0).getName());
        }

        @Test
        @DisplayName("detail 404")
        void detailMissing() {
            when(sysDeptMapper.selectOneById(9L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.detail(9L));
        }

        @Test
        @DisplayName("detail 成功")
        void detailOk() {
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            assertEquals("Root", service.detail(1L).getName());
        }
    }

    @Nested
    @DisplayName("写操作")
    class Write {
        @Test
        @DisplayName("create 根部门")
        void createRoot() {
            when(sysDeptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
                ((SysDept) inv.getArgument(0)).setId(10L);
                return 1;
            });
            AdminDeptDTO dto = new AdminDeptDTO();
            dto.setName("  Eng  ");
            assertEquals(10L, service.create(dto, 99L));
        }

        @Test
        @DisplayName("create 校验父部门")
        void createWithParent() {
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            when(sysDeptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
                ((SysDept) inv.getArgument(0)).setId(11L);
                return 1;
            });
            AdminDeptDTO dto = new AdminDeptDTO();
            dto.setParentId(1L);
            dto.setName("Child");
            dto.setSortOrder(2);
            dto.setStatus(1);
            assertEquals(11L, service.create(dto, 1L));
        }

        @Test
        @DisplayName("update 禁止父部门为自己")
        void updateSelfParent() {
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            AdminDeptDTO dto = new AdminDeptDTO();
            dto.setParentId(1L);
            dto.setName("Root");
            CustomException ex = assertThrows(CustomException.class, () -> service.update(1L, dto, 1L));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("update 成功")
        void updateOk() {
            SysDept d = dept(2L, 1L, "Old");
            when(sysDeptMapper.selectOneById(2L)).thenReturn(d);
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            AdminDeptDTO dto = new AdminDeptDTO();
            dto.setParentId(1L);
            dto.setName("New");
            dto.setSortOrder(3);
            dto.setStatus(0);
            service.update(2L, dto, 9L);
            assertEquals("New", d.getName());
            assertEquals(0, d.getStatus());
            verify(sysDeptMapper).update(d);
        }

        @Test
        @DisplayName("delete 有子部门失败")
        void deleteHasChildren() {
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            when(sysDeptMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            assertThrows(CustomException.class, () -> service.delete(1L));
        }

        @Test
        @DisplayName("delete 有用户失败")
        void deleteHasUsers() {
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            when(sysDeptMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            assertThrows(CustomException.class, () -> service.delete(1L));
        }

        @Test
        @DisplayName("delete 成功")
        void deleteOk() {
            when(sysDeptMapper.selectOneById(1L)).thenReturn(dept(1L, 0L, "Root"));
            when(sysDeptMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            service.delete(1L);
            verify(sysDeptMapper).deleteById(1L);
        }
    }
}
