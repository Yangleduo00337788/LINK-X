// 异常处理包
package com.linkx.server.exception;

// 统一响应封装，异常时也返回相同 JSON 结构
import com.linkx.server.common.Result;
// Lombok 日志注解，生成 log 对象
import lombok.extern.slf4j.Slf4j;
// 标记方法为特定异常类型的处理器
import org.springframework.web.bind.annotation.ExceptionHandler;
// 全局 REST 异常Advice，作用于所有 @RestController
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 将 Controller / Service 抛出的异常统一转换为 Result JSON，
 * 避免向前端暴露堆栈信息，并保证响应格式一致。
 * </p>
 */
@Slf4j // 注入 Slf4j Logger，变量名为 log
@RestControllerAdvice // 全局捕获 @RestController 层抛出的异常
public class GlobalExceptionHandler {

    /**
     * 处理预期的业务异常（如用户名已存在、密码错误）。
     *
     * @param e CustomException 实例
     * @return 带具体 code 和 message 的 Result
     */
    @ExceptionHandler(CustomException.class) // 仅处理 CustomException 及其子类
    public Result<?> handleCustomException(CustomException e) {
        log.warn("业务异常: {}", e.getMessage()); // WARN 级别记录，便于排查业务问题
        return Result.error(e.getCode(), e.getMessage()); // 将业务码与消息原样返回前端
    }

    /**
     * 兜底处理所有未捕获的 Exception。
     *
     * @param e 任意异常
     * @return 固定 500 错误，隐藏内部细节
     */
    @ExceptionHandler(Exception.class) // 捕获其余所有 Exception
    public Result<?> handleException(Exception e) {
        log.error("系统内部异常: ", e); // ERROR 级别并打印完整堆栈，便于运维排查
        return Result.error(500, "系统内部繁忙，请稍后再试"); // 对用户隐藏具体异常信息
    }
}
