// JWT 工具类所在包
package com.linkx.server.common;

// JJWT 库：解析后的 Claims（JWT 载荷）
import io.jsonwebtoken.Claims;
// JJWT 库：构建与解析 JWT 的核心 API
import io.jsonwebtoken.Jwts;
// JJWT 库：根据密钥生成 HMAC 签名 Key
import io.jsonwebtoken.security.Keys;
// Spring 注解：从 application.yml 注入配置值
import org.springframework.beans.factory.annotation.Value;
// Spring 注解：注册为 IoC 容器中的单例组件
import org.springframework.stereotype.Component;

// JDK 加密 API：HMAC 签名所需的 SecretKey 类型
import javax.crypto.SecretKey;
// 标准字符集，确保密钥字节编码一致
import java.nio.charset.StandardCharsets;
// 日期类型，用于设置 JWT 签发时间与过期时间
import java.util.Date;
// HashMap，用于存放 JWT 自定义 Claims
import java.util.HashMap;
// Map 接口，Claims 容器类型
import java.util.Map;

/**
 * JWT（JSON Web Token）工具类。
 * <p>
 * 负责 AccessToken / RefreshToken 的生成、解析与用户 ID 提取。
 * 配置项来自 application.yml 中的 linkx.jwt 节点。
 * </p>
 */
@Component // 注册为 Spring Bean，可在 Service、Interceptor 中注入使用
public class JwtUtils {

    // 从配置文件读取 JWT 签名密钥字符串
    @Value("${linkx.jwt.secret}")
    private String secret;

    // 从配置文件读取 AccessToken 有效期（毫秒），默认 2 小时
    @Value("${linkx.jwt.access-expire}")
    private Long accessExpire;

    // 从配置文件读取 RefreshToken 有效期（毫秒），默认 7 天
    @Value("${linkx.jwt.refresh-expire}")
    private Long refreshExpire;

    /**
     * 将配置中的 secret 字符串转换为 HMAC-SHA 签名密钥。
     *
     * @return 可用于 signWith / verifyWith 的 SecretKey
     */
    private SecretKey getSecretKey() {
        // 使用 UTF-8 字节数组生成密钥，与签发/验签保持一致
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成短期 AccessToken，用于日常 API 鉴权。
     *
     * @param userId   用户主键 ID
     * @param username 登录账号
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(Long userId, String username) {
        // 复用通用生成逻辑，过期时间取 accessExpire
        return generateToken(userId, username, accessExpire);
    }

    /**
     * 生成长期 RefreshToken，用于后续刷新 AccessToken（接口待实现）。
     *
     * @param userId   用户主键 ID
     * @param username 登录账号
     * @return 签名后的 JWT 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        // 复用通用生成逻辑，过期时间取 refreshExpire
        return generateToken(userId, username, refreshExpire);
    }

    /**
     * 通用 Token 生成方法。
     *
     * @param userId     用户 ID，写入 Claims
     * @param username   用户名，写入 Claims
     * @param expireTime 有效时长（毫秒）
     * @return 紧凑格式的 JWT 字符串
     */
    private String generateToken(Long userId, String username, Long expireTime) {
        // 创建 Claims 容器，存放自定义业务字段
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);     // 拦截器后续通过此字段识别当前用户
        claims.put("username", username); // 冗余存储用户名，便于日志与调试

        Date now = new Date(); // 当前时间作为签发时间（iat）
        // 过期时间 = 当前时间戳 + 配置的有效毫秒数
        Date expiration = new Date(now.getTime() + expireTime);

        // 使用 JJWT Builder 链式构建 JWT
        return Jwts.builder()
                .claims(claims)           // 设置自定义载荷
                .issuedAt(now)            // 设置签发时间
                .expiration(expiration)   // 设置过期时间
                .signWith(getSecretKey()) // 使用 HMAC 密钥签名
                .compact();               // 序列化为 header.payload.signature 字符串
    }

    /**
     * 解析并验签 JWT，返回 Claims 载荷。
     *
     * @param token 客户端传入的 JWT 字符串（不含 Bearer 前缀）
     * @return 验签成功后的 Claims 对象
     * @throws io.jsonwebtoken.JwtException 签名无效、过期或格式错误时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey()) // 使用相同密钥验签，防止篡改
                .build()
                .parseSignedClaims(token)   // 解析已签名的 JWT
                .getPayload();              // 取出载荷部分（Claims）
    }

    /**
     * 从 Token 中提取用户 ID。
     *
     * @param token JWT 字符串
     * @return 注册时写入 Claims 的 userId
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);              // 先完成验签与解析
        return claims.get("userId", Long.class);        // 按 Long 类型读取 userId 字段
    }
}
