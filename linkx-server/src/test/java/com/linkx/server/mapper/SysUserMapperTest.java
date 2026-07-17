package com.linkx.server.mapper;

import com.linkx.server.entity.SysUser;
import com.linkx.server.support.BaseIntegrationTest;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SysUserMapper 数据库访问层测试
 */
@DisplayName("SysUserMapper 测试")
class SysUserMapperTest extends BaseIntegrationTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Nested
    @DisplayName("CRUD 操作测试")
    class CrudTests {

        @Test
        @DisplayName("插入用户应成功")
        void insertUser_success() {
            SysUser user = SysUser.builder()
                    .username("mapper_test_" + System.nanoTime())
                    .password("hashedPassword")
                    .nickname("Mapper测试")
                    .status(1)
                    .deleted(0)
                    .build();

            int rows = sysUserMapper.insert(user);

            assertEquals(1, rows);
            assertNotNull(user.getId());
            assertTrue(user.getId() > 0);
        }

        @Test
        @DisplayName("根据ID查询用户应成功")
        void selectById_success() {
            // 先插入
            SysUser user = SysUser.builder()
                    .username("selectbyid_" + System.nanoTime())
                    .password("pass123")
                    .nickname("Select测试")
                    .status(1)
                    .deleted(0)
                    .build();
            sysUserMapper.insert(user);

            // 再查询
            SysUser found = sysUserMapper.selectOneById(user.getId());

            assertNotNull(found);
            assertEquals(user.getId(), found.getId());
            assertEquals("Select测试", found.getNickname());
        }

        @Test
        @DisplayName("更新用户应成功")
        void updateUser_success() {
            // 先插入
            SysUser user = SysUser.builder()
                    .username("update_" + System.nanoTime())
                    .password("oldPass")
                    .nickname("原昵称")
                    .status(1)
                    .deleted(0)
                    .build();
            sysUserMapper.insert(user);

            // 更新
            user.setNickname("新昵称");
            int rows = sysUserMapper.update(user);

            assertEquals(1, rows);

            // 验证
            SysUser updated = sysUserMapper.selectOneById(user.getId());
            assertEquals("新昵称", updated.getNickname());
        }

        @Test
        @DisplayName("删除用户应成功")
        void deleteUser_success() {
            // 先插入
            SysUser user = SysUser.builder()
                    .username("delete_" + System.nanoTime())
                    .password("pass")
                    .nickname("待删除")
                    .status(1)
                    .deleted(0)
                    .build();
            sysUserMapper.insert(user);

            int rows = sysUserMapper.deleteById(user.getId());

            assertEquals(1, rows);
            assertNull(sysUserMapper.selectOneById(user.getId()));
        }

        @Test
        @DisplayName("查询不存在的用户应返回null")
        void selectNonExistent_returnsNull() {
            SysUser found = sysUserMapper.selectOneById(999999999L);
            assertNull(found);
        }
    }

    @Nested
    @DisplayName("条件查询测试")
    class QueryWrapperTests {

        @Test
        @DisplayName("按用户名查询应成功")
        void selectByUsername_success() {
            String username = "queryuser_" + System.nanoTime();
            SysUser user = SysUser.builder()
                    .username(username)
                    .password("pass")
                    .nickname("查询测试")
                    .status(1)
                    .deleted(0)
                    .build();
            sysUserMapper.insert(user);

            QueryWrapper query = QueryWrapper.create()
                    .where(SysUser::getUsername).eq(username);
            SysUser found = sysUserMapper.selectOneByQuery(query);

            assertNotNull(found);
            assertEquals(username, found.getUsername());
            assertEquals("查询测试", found.getNickname());
        }

        @Test
        @DisplayName("按用户名模糊查询应成功")
        void selectByUsernameLike_success() {
            String prefix = "likeuser_" + System.nanoTime();
            for (int i = 1; i <= 3; i++) {
                SysUser user = SysUser.builder()
                        .username(prefix + "_" + i)
                        .password("pass")
                        .nickname("模糊用户" + i)
                        .status(1)
                        .deleted(0)
                        .build();
                sysUserMapper.insert(user);
            }

            QueryWrapper query = QueryWrapper.create()
                    .where(SysUser::getUsername).like(prefix + "%");
            var list = sysUserMapper.selectListByQuery(query);

            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("多条件查询应成功")
        void selectWithMultipleConditions_success() {
            String username = "multi_" + System.nanoTime();
            SysUser user = SysUser.builder()
                    .username(username)
                    .password("pass")
                    .nickname("多条件测试")
                    .status(1)
                    .deleted(0)
                    .build();
            sysUserMapper.insert(user);

            QueryWrapper query = QueryWrapper.create()
                    .where(SysUser::getUsername).eq(username)
                    .and(SysUser::getNickname).like("%多条件%");
            SysUser found = sysUserMapper.selectOneByQuery(query);

            assertNotNull(found);
            assertEquals(username, found.getUsername());
        }
    }

    @Nested
    @DisplayName("BaseMapper 方法测试")
    class BaseMapperMethodTests {

        @Test
        @DisplayName("selectAll应返回列表")
        void selectAll_works() {
            var list = sysUserMapper.selectAll();

            assertNotNull(list);
        }

        @Test
        @DisplayName("根据ID列表查询应成功")
        void selectListByIds_works() {
            // 先插入
            SysUser user = SysUser.builder()
                    .username("ids_" + System.nanoTime())
                    .password("pass")
                    .nickname("IDs测试")
                    .status(1)
                    .deleted(0)
                    .build();
            sysUserMapper.insert(user);

            var list = sysUserMapper.selectListByIds(java.util.List.of(user.getId()));

            assertTrue(list.size() >= 1);
            assertEquals(user.getId(), list.get(0).getId());
        }
    }
}
