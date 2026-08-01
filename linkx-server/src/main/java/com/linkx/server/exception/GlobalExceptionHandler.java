package com.linkx.server.exception;

import com.linkx.server.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Result<?>> handleCustomException(CustomException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(mapStatus(e.getCode()))
                .body(Result.error(e.getCode(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<?>> handleValidationException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException manve && manve.getBindingResult().hasErrors()) {
            message = manve.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        } else if (e instanceof BindException be && be.getBindingResult().hasErrors()) {
            message = be.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, message));
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<Result<?>> handleMultipartException(Exception e) {
        log.warn("上传失败: {}", e.getMessage());
        String message = e instanceof MaxUploadSizeExceededException
                ? "文件大小超过限制"
                : "文件上传解析失败，请重试";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, message));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<?>> handleNotFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(404, "接口不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Result.error(405, "请求方法不允许"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, "请求体格式错误"));
    }

    /**
     * 客户端主动断开（切页、取消图片加载等）时常见，不算服务端故障。
     * 响应可能已写入 image/*，不可再回写 JSON Result。
     */
    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientAbort(Exception e) {
        log.debug("客户端中断连接: {}", rootMessage(e));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        if (isClientAbort(e)) {
            log.debug("客户端中断连接: {}", rootMessage(e));
            return null;
        }
        log.error("系统内部异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "系统内部繁忙，请稍后再试"));
    }

    private static boolean isClientAbort(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ClientAbortException || t instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("broken pipe")
                        || m.contains("connection reset")
                        || m.contains("connection was aborted")
                        || m.contains("中止了一个已建立的连接")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.toString();
    }

    private HttpStatus mapStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 405 -> HttpStatus.METHOD_NOT_ALLOWED;
            case 428 -> HttpStatus.PRECONDITION_REQUIRED;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> code >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        };
    }
}
