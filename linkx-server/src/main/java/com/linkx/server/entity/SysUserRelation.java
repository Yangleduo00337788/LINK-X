// 实体类包
package com.linkx.server.entity;

// MyBatis-Flex 列配置注解
import com.mybatisflex.annotation.Column;
// 主键注解
import com.mybatisflex.annotation.Id;
// 主键策略类型
import com.mybatisflex.annotation.KeyType;
// 表映射注解
import com.mybatisflex.annotation.Table;
// 雪花 ID 生成器
import com.mybatisflex.core.keygen.KeyGenerators;
// Lombok 全参构造
import lombok.AllArgsConstructor;
// Lombok Builder
import lombok.Builder;
// Lombok 数据类
import lombok.Data;
// Lombok 无参构造
import lombok.NoArgsConstructor;

// 可序列化接口
import java.io.Serializable;
// 时间字段类型
import java.util.Date;

/**
 * 用户好友关系实体，对应数据库表 sys_user_relation。
 * <p>
 * 描述两个用户之间的好友关系，含备注与拉黑状态。
 * 当前仅有表结构与 Mapper，业务 API 尚未实现。
 * </p>
 */
@Data // 自动生成属性访问方法
@Builder // 便于后续业务代码构建关系记录
@NoArgsConstructor // ORM 框架实例化需要
@AllArgsConstructor // 配合 Builder 使用
@Table("sys_user_relation") // 映射好友关系表
public class SysUserRelation implements Serializable {

    // 序列化版本号
    private static final long serialVersionUID = 1L;

    // 关系记录主键，雪花算法生成
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    // 发起方/所属用户 ID，对应 sys_user.id
    private Long userId;

    // 好友用户 ID，对应 sys_user.id
    private Long friendId;

    // 好友备注名，可为空
    private String remark;

    // 关系状态：1=正常好友，2=已拉黑
    private Integer status;

    // 成为好友的时间
    private Date createTime;

    // 关系信息最后更新时间
    private Date updateTime;

    // 逻辑删除：0=有效，1=已删除
    @Column(isLogicDelete = true)
    private Integer deleted;
}
