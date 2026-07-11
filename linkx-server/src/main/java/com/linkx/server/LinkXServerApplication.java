// 声明当前类所在的包路径，对应目录 com/linkx/server/
package com.linkx.server;

// 导入 MyBatis-Flex 提供的 Mapper 扫描注解，用于自动注册 Mapper 接口
import com.linkx.server.config.LinkxProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * LinkX 后端服务启动入口类。
 * <p>
 * 负责启动 Spring Boot 容器，并扫描注册 MyBatis Mapper 接口。
 * </p>
 */
@SpringBootApplication
@MapperScan("com.linkx.server.mapper")
@EnableConfigurationProperties(LinkxProperties.class)
@EnableAsync
public class LinkXServerApplication {

    /**
     * Java 程序主入口方法。
     *
     * @param args 命令行启动参数（当前项目未使用，保留标准签名）
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用，加载配置、初始化 IoC 容器并启动内嵌 Tomcat
        SpringApplication.run(LinkXServerApplication.class, args);
        // 控制台输出启动成功提示，便于开发者在日志中快速确认服务已就绪
        System.out.println("(♥◠‿◠)ﾉﾞ  LinkX 单体后端服务启动成功   ლ(´ڡ`ლ)ﾞ");
    }

}
