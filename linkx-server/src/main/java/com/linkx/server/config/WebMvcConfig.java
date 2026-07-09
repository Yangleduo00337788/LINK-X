// Spring MVC 配置包
package com.linkx.server.config;

// 登录鉴权拦截器
import com.linkx.server.config.interceptor.LoginInterceptor;
// Lombok 构造器注入
import lombok.RequiredArgsConstructor;
// 标记为配置类，Spring 启动时加载
import org.springframework.context.annotation.Configuration;
// CORS 跨域配置 API
import org.springframework.web.servlet.config.annotation.CorsRegistry;
// 拦截器注册 API
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
// WebMvc 扩展接口
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局配置。
 * <p>
 * 注册登录拦截器与 CORS 跨域规则，供 Vue 前端（不同端口）调用后端 API。
 * </p>
 */
@Configuration // 声明为 Spring 配置 Bean
@RequiredArgsConstructor // 通过构造器注入 LoginInterceptor
public class WebMvcConfig implements WebMvcConfigurer {

    // JWT 登录校验拦截器，由 Spring 自动注入
    private final LoginInterceptor loginInterceptor;

    /**
     * 注册 HTTP 拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor) // 添加登录拦截器实例
                .addPathPatterns("/**")          // 拦截所有路径（相对于 context-path 之后）
                .excludePathPatterns(            // 以下路径无需 Token 即可访问
                        "/auth/login",           // 登录接口
                        "/auth/register",        // 注册接口
                        "/error"                 // Spring Boot 默认错误页
                );
    }

    /**
     * 配置跨域资源共享（CORS）。
     * <p>
     * 允许前端开发服务器（如 localhost:5173）携带 Cookie/Authorization 访问后端。
     * </p>
     *
     * @param registry CORS 注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                              // 对所有 API 路径生效
                .allowedOriginPatterns("*")                     // 允许任意来源（生产环境应收紧）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的 HTTP 方法
                .allowedHeaders("*")                            // 允许任意请求头（含 Authorization）
                .allowCredentials(true)                           // 允许携带凭证
                .maxAge(3600);                                  // 预检 OPTIONS 结果缓存 1 小时
    }
}
