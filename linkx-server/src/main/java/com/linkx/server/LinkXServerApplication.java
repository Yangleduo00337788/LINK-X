// 声明当前类所在的包路径，对应目录 com/linkx/server/
package com.linkx.server;

// 导入 MyBatis-Flex 提供的 Mapper 扫描注解，用于自动注册 Mapper 接口
import com.linkx.server.config.LinkxProperties;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * LinkX 后端服务启动入口类。
 * <p>
 * 负责启动 Spring Boot 容器，并扫描注册 MyBatis Mapper 接口。
 * </p>
 */
@SpringBootApplication(exclude = {
        // 自定义 JavaMailSender（见 MailConfig），避免与自动配置冲突
        MailSenderAutoConfiguration.class,
        MailSenderValidatorAutoConfiguration.class
})
@MapperScan("com.linkx.server.mapper")
@EnableConfigurationProperties(LinkxProperties.class)
@EnableAsync
@EnableScheduling
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

/**
 * JWT Secret 启动校验组件
 * 确保 JWT 密钥长度符合 HMAC-SHA 要求（至少 256 位 / 32 字节）
 */
@Slf4j
@Component
@RequiredArgsConstructor
class JwtSecretValidator {

    private static final int MIN_SECRET_LENGTH_BYTES = 32;

    private final LinkxProperties linkxProperties;

    @PostConstruct
    public void validateJwtSecret() {
        String secret = linkxProperties.getJwt().getSecret();

        if (secret == null || secret.isEmpty()) {
            log.error("[安全错误] JWT_SECRET 环境变量未设置，应用启动失败");
            throw new IllegalStateException(
                    "JWT Secret 不能为空。请设置 JWT_SECRET 环境变量，建议长度至少 32 个字符。" +
                    "可以使用以下命令生成安全密钥: openssl rand -base64 32"
            );
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < MIN_SECRET_LENGTH_BYTES) {
            log.error("[安全错误] JWT_SECRET 长度不足，当前 {} 字节，要求至少 {} 字节",
                    secretBytes.length, MIN_SECRET_LENGTH_BYTES);
            throw new IllegalStateException(
                    String.format("JWT Secret 长度不足。当前: %d 字节, 要求: 至少 %d 字节 (256 位)。\n" +
                            "请设置更长的 JWT_SECRET 环境变量，建议: openssl rand -base64 32",
                            secretBytes.length, MIN_SECRET_LENGTH_BYTES)
            );
        }

        // 验证密钥是否可用于 HMAC-SHA256
        try {
            SecretKey key = Keys.hmacShaKeyFor(secretBytes);
            log.info("[安全配置] JWT Secret 校验通过，密钥长度: {} 字节", secretBytes.length);
        } catch (Exception e) {
            log.error("[安全错误] JWT Secret 格式无效", e);
            throw new IllegalStateException("JWT Secret 格式无效，无法创建有效的 HMAC 密钥", e);
        }

        // 安全检查：警告弱密钥
        if (secret.length() < 32 || !containsMixedCharacters(secret)) {
            log.warn("[安全警告] JWT Secret 强度可能不足，建议使用随机生成的长密钥");
        }
    }

    private boolean containsMixedCharacters(String secret) {
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : secret.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        // 至少包含三种类型字符
        int typeCount = 0;
        if (hasUpper) typeCount++;
        if (hasLower) typeCount++;
        if (hasDigit) typeCount++;
        if (hasSpecial) typeCount++;
        return typeCount >= 3;
    }
}
