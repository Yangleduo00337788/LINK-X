// 自定义异常包
package com.linkx.server.exception;


/**
 * 作者：yangleduo
 */
// Lombok：为 code 字段生成 getter
import lombok.Getter;

/**
 * 自定义业务异常。
 * <p>
 * Service 层在业务校验失败时抛出，由 GlobalExceptionHandler 捕获并转为 Result JSON。
 * </p>
 */
@Getter // 生成 getCode()，供异常处理器读取错误码
public class CustomException extends RuntimeException {

    // 业务错误码，如 400（参数/业务错误）、401（未登录）、403（无权限）、428（需二次验证）
    private final Integer code;

    /** 可选载荷（如 step-up 可用方式），失败时也可带回 data */
    private final Object data;

    /**
     * 仅传消息的构造器，默认错误码 500。
     *
     * @param message 异常描述，会返回给前端
     */
    public CustomException(String message) {
        this(500, message, null);
    }

    /**
     * 指定错误码与消息的构造器，业务场景常用。
     *
     * @param code    HTTP 风格或自定义业务码
     * @param message 返回给前端的提示文案
     */
    public CustomException(Integer code, String message) {
        this(code, message, null);
    }

    /**
     * 带载荷的业务异常（如 428 二次验证）。
     */
    public CustomException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}
