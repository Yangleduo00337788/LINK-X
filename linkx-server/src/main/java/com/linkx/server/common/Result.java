// 统一响应封装类所在包
package com.linkx.server.common;

// Lombok 注解：自动生成 getter/setter/toString/equals/hashCode
import lombok.Data;

// Serializable 接口：使对象可序列化，便于网络传输与缓存
import java.io.Serializable;

/**
 * 统一 API 响应封装类。
 * <p>
 * 前后端约定响应格式：{ "code": 200, "message": "success", "data": {} }
 * </p>
 *
 * @param <T> data 字段的泛型类型，不同接口返回不同业务数据
 */
@Data // 编译期生成常用方法，减少样板代码
public class Result<T> implements Serializable {

    // 序列化版本号，类结构变更时可显式维护兼容性
    private static final long serialVersionUID = 1L;

    // 业务状态码：200 表示成功，4xx/5xx 表示客户端/服务端错误
    private Integer code;
    // 提示信息：成功时为 "success"，失败时为具体错误描述
    private String message;
    // 实际业务数据载荷，失败时通常为 null
    private T data;

    /**
     * 无参构造器。
     * Jackson 反序列化及框架反射实例化时需要。
     */
    public Result() {
    }

    /**
     * 私有全参构造器，仅通过静态工厂方法创建实例，保证构造方式统一。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    业务数据
     */
    private Result(Integer code, String message, T data) {
        this.code = code;       // 赋值状态码
        this.message = message; // 赋值提示信息
        this.data = data;       // 赋值业务数据
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 泛型占位
     * @return code=200, message=success, data=null
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null); // 标准成功响应，不带 data
    }

    /**
     * 构造带数据的成功响应。
     *
     * @param data 要返回给前端的业务对象
     * @param <T>  数据类型
     * @return code=200 且 data 不为空的 Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data); // 登录接口等场景返回 TokenVO 等对象
    }

    /**
     * 构造指定错误码的失败响应。
     *
     * @param code    自定义错误码（如 400、401、403）
     * @param message 错误描述
     * @param <T>     泛型占位
     * @return 失败 Result，data 固定为 null
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null); // 业务异常走此分支，前端可读取 code 与 message
    }

    /**
     * 构造默认 500 错误的失败响应。
     *
     * @param message 错误描述
     * @param <T>     泛型占位
     * @return code=500 的失败 Result
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null); // 未指定 code 时默认服务器内部错误
    }
}
