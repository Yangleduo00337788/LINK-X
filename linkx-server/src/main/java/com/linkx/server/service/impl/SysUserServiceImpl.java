// 业务服务实现类包
package com.linkx.server.service.impl;

// JWT 工具
import com.linkx.server.common.JwtUtils;
// 登录 DTO
import com.linkx.server.controller.dto.LoginDTO;
// 注册 DTO
import com.linkx.server.controller.dto.RegisterDTO;
// Token 响应 VO
import com.linkx.server.controller.vo.TokenVO;
// 用户信息 VO
import com.linkx.server.controller.vo.UserInfoVO;
// 用户实体
import com.linkx.server.entity.SysUser;
// 业务异常
import com.linkx.server.exception.CustomException;
// 用户 Mapper
import com.linkx.server.mapper.SysUserMapper;
// 用户 Service 接口
import com.linkx.server.service.SysUserService;
// MyBatis-Flex Service 基类，提供 save/queryChain 等实现
import com.mybatisflex.spring.service.impl.ServiceImpl;
// Lombok 构造器注入
import lombok.RequiredArgsConstructor;
// BCrypt 密码哈希库
import org.mindrot.jbcrypt.BCrypt;
// 标记为 Spring 业务 Bean
import org.springframework.stereotype.Service;

/**
 * 系统用户业务服务实现类。
 * <p>
 * 实现注册与登录核心逻辑：查重、密码加密、JWT 签发。
 * </p>
 */
@Service // 注册为 Service Bean，供 AuthController 注入
@RequiredArgsConstructor // 构造器注入 JwtUtils（Mapper 由父类 ServiceImpl 管理）
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    // JWT 工具，登录成功后生成 AccessToken 与 RefreshToken
    private final JwtUtils jwtUtils;

    /**
     * 用户注册实现。
     *
     * @param registerDTO 注册参数
     */
    @Override
    public void register(RegisterDTO registerDTO) {
        // 1. 检查用户名是否已存在（逻辑删除的记录由 MyBatis-Flex 自动过滤）
        long count = queryChain()
                .where(SysUser::getUsername).eq(registerDTO.getUsername()) // 按 username 等值查询
                .count(); // 返回匹配行数
        if (count > 0) {
            // 用户名冲突，抛出 400 业务异常
            throw new CustomException(400, "用户名已存在");
        }

        // 2. 使用 BCrypt 对明文密码进行单向哈希
        String salt = BCrypt.gensalt(); // 生成随机盐
        String hashPassword = BCrypt.hashpw(registerDTO.getPassword(), salt); // 加盐哈希

        // 3. 构建用户实体对象
        SysUser user = SysUser.builder()
                .username(registerDTO.getUsername())   // 设置登录账号
                .password(hashPassword)                // 存储哈希密码，非明文
                .nickname(registerDTO.getNickname())   // 设置昵称
                // 默认头像：DiceBear 根据用户名生成唯一 SVG 头像
                .avatar("https://api.dicebear.com/7.x/adventurer/svg?seed=" + registerDTO.getUsername())
                .status(1)                             // 新用户默认状态：正常
                .build();                              // 完成 Builder 构建

        // 4. 调用父类 save 方法插入数据库（ID 由雪花算法自动生成）
        save(user);
    }

    /**
     * 用户登录实现。
     *
     * @param loginDTO 登录参数
     * @return TokenVO 双 Token + 用户信息
     */
    @Override
    public TokenVO login(LoginDTO loginDTO) {
        // 1. 根据用户名查询唯一用户记录
        SysUser user = queryChain()
                .where(SysUser::getUsername).eq(loginDTO.getUsername())
                .one(); // 期望 0 或 1 条，多条会抛异常
        if (user == null) {
            // 用户不存在，与密码错误使用相同提示，避免泄露账号是否存在
            throw new CustomException(400, "用户名或密码错误");
        }

        // 2. 使用 BCrypt 校验明文密码与库中哈希是否匹配
        if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            throw new CustomException(400, "用户名或密码错误");
        }

        // 3. 检查账号是否被管理员停用
        if (user.getStatus() != 1) {
            throw new CustomException(403, "账号已被停用");
        }

        // 4. 签发 AccessToken（短效）与 RefreshToken（长效）
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        // 5. 组装返回给前端的用户信息（不含 password）
        UserInfoVO userInfo = UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .signature(user.getSignature())
                .build();

        // 6. 构建 TokenVO 并返回
        return TokenVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userInfo)
                .build();
    }
}
