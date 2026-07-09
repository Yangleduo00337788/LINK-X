// 拦截器包
package com.linkx.server.config.interceptor;

// JWT 解析工具
import com.linkx.server.common.JwtUtils;
// 未登录/Token 无效时抛出的业务异常
import com.linkx.server.exception.CustomException;
// Servlet 请求对象
import jakarta.servlet.http.HttpServletRequest;
// Servlet 响应对象
import jakarta.servlet.http.HttpServletResponse;
// Lombok 构造器注入
import lombok.RequiredArgsConstructor;
// 注册为 Spring 组件
import org.springframework.stereotype.Component;
// 字符串工具，判断 Token 是否为空
import org.springframework.util.StringUtils;
// Spring MVC 处理器拦截器接口
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器。
 * <p>
 * 在请求进入 Controller 之前校验 Authorization 头中的 JWT，
 * 校验通过后将 userId 写入 request 属性供后续使用。
 * </p>
 */
@Component // 注册为 Bean，供 WebMvcConfig 注入
@RequiredArgsConstructor // 构造器注入 JwtUtils
public class LoginInterceptor implements HandlerInterceptor {

    // JWT 工具类，负责解析 Token 并提取 userId
    private final JwtUtils jwtUtils;

    /**
     * 请求预处理：Controller 方法执行前调用。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  目标处理器（Controller 方法）
     * @return true 放行；false 中断（本类通过抛异常中断）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 浏览器 CORS 预检请求不带 Token，直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true; // 返回 true 表示继续执行后续过滤器链
        }

        // 2. 从请求头读取 Authorization 字段
        String token = request.getHeader("Authorization");
        // 若 Token 缺失或全是空白，视为未登录
        if (!StringUtils.hasText(token)) {
            throw new CustomException(401, "未登录或登录已过期"); // 抛异常，由全局处理器返回 401 JSON
        }

        // 3. 前端通常发送 "Bearer xxx"，需去掉前缀得到纯 JWT
        if (token.startsWith("Bearer ")) {
            token = token.substring(7); // "Bearer " 长度为 7
        }

        // 4. 解析 JWT 并验证签名与有效期
        try {
            Long userId = jwtUtils.getUserIdFromToken(token); // 验签失败或过期会抛 JwtException
            // 将当前登录用户 ID 存入 request，Controller 可通过 request.getAttribute("userId") 获取
            request.setAttribute("userId", userId);
            return true; // 鉴权通过，继续访问 Controller
        } catch (Exception e) {
            // 捕获签名错误、过期、格式非法等所有解析异常，统一返回 401
            throw new CustomException(401, "登录已过期，请重新登录");
        }
    }
}
