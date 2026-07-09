// 实体类包：与数据库表一一对应的 Java 对象
package com.linkx.server.entity;

// MyBatis-Flex 列注解，可配置逻辑删除等
import com.mybatisflex.annotation.Column;
// 主键注解
import com.mybatisflex.annotation.Id;
// 主键生成策略类型
import com.mybatisflex.annotation.KeyType;
// 表名映射注解
import com.mybatisflex.annotation.Table;
// 雪花 ID 生成器常量
import com.mybatisflex.core.keygen.KeyGenerators;
// Lombok：全参构造器
import lombok.AllArgsConstructor;
// Lombok：Builder 模式
import lombok.Builder;
// Lombok：getter/setter 等
import lombok.Data;
// Lombok：无参构造器
import lombok.NoArgsConstructor;

// 序列化接口
import java.io.Serializable;
// 日期类型，映射 DATETIME 字段
import java.util.Date;

/**
 * 系统用户实体，对应数据库表 sys_user。
 * <p>
 * 存储账号、密码哈希、昵称、头像等用户基础信息。
 * </p>
 */
@Data // 生成访问器，MyBatis-Flex 通过 getter/setter 读写属性
@Builder // 支持链式构建，注册时使用 SysUser.builder()...
@NoArgsConstructor // MyBatis 反射实例化需要无参构造
@AllArgsConstructor // 配合 Builder 生成全参构造
@Table("sys_user") // 指定映射的数据库表名
public class SysUser implements Serializable {

    // 序列化版本号
    private static final long serialVersionUID = 1L;

    // 主键 ID，使用雪花算法自动生成，无需手动赋值
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    // 登录账号，唯一索引 uk_username
    private String username;

    // BCrypt 加密后的密码哈希，禁止明文存储
    private String password;

    // 用户昵称，界面展示名
    private String nickname;

    // 头像 URL 地址
    private String avatar;

    // 个性签名，可为空
    private String signature;

    // 账号状态：1=正常，0=停用
    private Integer status;

    // 记录创建时间，数据库默认 CURRENT_TIMESTAMP
    private Date createTime;

    // 记录最后更新时间，数据库 ON UPDATE CURRENT_TIMESTAMP
    private Date updateTime;

    // 创建人 ID，审计字段，可为空
    private Long createBy;

    // 最后修改人 ID，审计字段，可为空
    private Long updateBy;

    // 逻辑删除标记：0=未删除，1=已删除；查询时自动过滤 deleted=1 的记录
    @Column(isLogicDelete = true)
    private Integer deleted;
}
