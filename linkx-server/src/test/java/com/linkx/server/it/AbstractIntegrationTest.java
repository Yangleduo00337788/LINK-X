package com.linkx.server.it;

import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 集成测试基类：通过 docker CLI 拉起真实 MySQL 8.4 + Redis 7.2（单例跨测试类复用）。
 * <p>
 * 不使用 Testcontainers：Docker Desktop 29.x 的 CLI 认证代理会拦截 docker-java
 * 的命名管道请求（/info 返回 400 stub），而 docker CLI 自身可用，故直接托管容器。
 * 若外部已提供环境变量 LINKX_IT_MYSQL_URL / LINKX_IT_REDIS_HOST，则优先使用外部实例。
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(AbstractIntegrationTest.TestMailConfig.class)
public abstract class AbstractIntegrationTest {

    /** 测试环境邮件服务：生产实现为 @Profile("!test")，此处提供空实现 */
    @org.springframework.boot.test.context.TestConfiguration
    public static class TestMailConfig {
        @org.springframework.context.annotation.Bean
        public com.linkx.server.service.EmailService emailService() {
            return new com.linkx.server.service.EmailService() {
                public void sendRegisterCode(String to, String username, String code) { }
                public void sendPasswordResetCode(String to, String username, String code) { }
                public void sendWelcomeEmail(String to, String username, String nickname) { }
                public void sendPasswordChangedNotification(String to, String username, String ip) { }
                public void sendBindEmailCode(String to, String username, String code) { }
                public void sendAdminStepUpCode(String to, String username, String code) { }
                public void sendApprovalPendingNotification(String to, String displayName, String title, String stepName) { }
            };
        }
    }

    private static final String MYSQL_CONTAINER = "linkx-it-mysql";
    private static final String REDIS_CONTAINER = "linkx-it-redis";
    private static final int MYSQL_HOST_PORT = 33061;
    private static final int REDIS_HOST_PORT = 36379;

    private static volatile boolean initialized;

    protected static String mysqlUrl;
    protected static String mysqlUsername = "linkx";
    protected static String mysqlPassword = "linkx";
    protected static String redisHost;
    protected static int redisPort;

    protected static final String TEST_PASSWORD = "Passw0rd123";

    static {
        boot();
    }

    private static synchronized void boot() {
        if (initialized) {
            return;
        }
        try {
            String externalMysql = System.getenv("LINKX_IT_MYSQL_URL");
            if (externalMysql != null && !externalMysql.isBlank()) {
                mysqlUrl = externalMysql;
                mysqlUsername = env("LINKX_IT_MYSQL_USERNAME", "linkx");
                mysqlPassword = env("LINKX_IT_MYSQL_PASSWORD", "linkx");
                redisHost = env("LINKX_IT_REDIS_HOST", "127.0.0.1");
                redisPort = Integer.parseInt(env("LINKX_IT_REDIS_PORT", "6379"));
            } else {
                startDockerContainers();
            }
            initialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("集成测试环境（MySQL/Redis）启动失败", e);
        }
    }

    private static void startDockerContainers() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(AbstractIntegrationTest::stopContainers));

        exec("docker", "rm", "-f", MYSQL_CONTAINER);
        exec("docker", "rm", "-f", REDIS_CONTAINER);
        exec("docker", "run", "-d", "--name", MYSQL_CONTAINER,
                "-e", "MYSQL_DATABASE=linkx", "-e", "MYSQL_USER=linkx", "-e", "MYSQL_PASSWORD=linkx",
                "-e", "MYSQL_ROOT_PASSWORD=root", "-e", "TZ=Asia/Shanghai",
                "-p", MYSQL_HOST_PORT + ":3306", "mysql:8.4");
        exec("docker", "run", "-d", "--name", REDIS_CONTAINER,
                "-e", "TZ=Asia/Shanghai",
                "-p", REDIS_HOST_PORT + ":6379", "redis:7.2");

        mysqlUrl = "jdbc:mysql://127.0.0.1:" + MYSQL_HOST_PORT
                + "/linkx?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false";

        // 项目基线表结构由 init.sql 提供（Flyway 迁移从 V2 开始），先在空库上执行基线
        waitUntil("MySQL 就绪", Duration.ofMinutes(3), () -> {
            try (Connection ignored = DriverManager.getConnection(
                    "jdbc:mysql://127.0.0.1:" + MYSQL_HOST_PORT
                            + "/?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false",
                    "root", "root")) {
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        runBaselineSql();
        redisHost = "127.0.0.1";
        redisPort = REDIS_HOST_PORT;
        waitUntil("Redis 就绪", Duration.ofMinutes(1), () -> {
            String pong = execCapture("docker", "exec", REDIS_CONTAINER, "redis-cli", "ping");
            return "PONG".equalsIgnoreCase(pong.trim());
        });
    }

    /**
     * 在空库上执行仓库根目录的 init.sql 基线表结构，
     * 再按版本号顺序执行 V2..V125 迁移；因 init.sql 与迁移内容部分重叠，
     * 对 duplicate column/table/index 等"已存在"错误容忍跳过，其余错误立即失败。
     */
    private static void runBaselineSql() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MYSQL_HOST_PORT
                + "/?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false&allowMultiQueries=true";
        try (Connection conn = DriverManager.getConnection(url, "root", "root");
             java.sql.Statement st = conn.createStatement()) {
            st.execute(java.nio.file.Files.readString(java.nio.file.Path.of("init.sql"),
                    StandardCharsets.UTF_8));
        }
        java.nio.file.Path migrationDir = java.nio.file.Path.of(
                "src", "main", "resources", "db", "migration");
        List<java.nio.file.Path> migrations = new ArrayList<>();
        try (var files = java.nio.file.Files.list(migrationDir)) {
            files.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(java.util.Comparator.comparingInt(p ->
                            Integer.parseInt(p.getFileName().toString()
                                    .replaceAll("^V(\\d+)__.*", "$1"))))
                    .forEach(migrations::add);
        }
        String dbUrl = "jdbc:mysql://127.0.0.1:" + MYSQL_HOST_PORT + "/linkx"
                + "?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false";
        int applied = 0;
        int skipped = 0;
        try (Connection conn = DriverManager.getConnection(dbUrl, "root", "root");
             java.sql.Statement st = conn.createStatement()) {
            for (java.nio.file.Path migration : migrations) {
                String sql = stripComments(
                        java.nio.file.Files.readString(migration, StandardCharsets.UTF_8));
                for (String statement : sql.split(";")) {
                    statement = statement.trim();
                    if (statement.isEmpty()) {
                        continue;
                    }
                    try {
                        st.execute(statement);
                        applied++;
                    } catch (java.sql.SQLException e) {
                        if (isIdempotentSkip(e)) {
                            skipped++;
                        } else {
                            throw new IllegalStateException("迁移失败: "
                                    + migration.getFileName() + "\n" + statement, e);
                        }
                    }
                }
            }
        }
        System.out.println("[LinkX-IT] 迁移执行完成，语句 " + applied + " 条，幂等跳过 " + skipped + " 条");
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?m)^--.*$", "");
    }

    private static boolean isIdempotentSkip(java.sql.SQLException e) {
        int code = e.getErrorCode();
        // 1050 表已存在 / 1060 列重复 / 1061 索引重名 / 1091 列不存在(无法DROP)
        return code == 1050 || code == 1060 || code == 1061 || code == 1091;
    }

    private static void stopContainers() {
        try {
            exec("docker", "rm", "-f", MYSQL_CONTAINER);
            exec("docker", "rm", "-f", REDIS_CONTAINER);
        } catch (Exception ignored) {
            // JVM 退出阶段尽力清理即可
        }
    }

    private static void waitUntil(String what, Duration timeout, BooleanSupplier probe) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (probe.getAsBoolean()) {
                    System.out.println("[LinkX-IT] " + what);
                    return;
                }
            } catch (Exception ignored) {
                // 探测异常视为未就绪，继续重试
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException(what + "等待超时（" + timeout + "）");
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static void exec(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!p.waitFor(2, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new IllegalStateException("命令超时: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("命令失败（exit=" + p.exitValue() + "）: "
                    + String.join(" ", cmd) + "\n" + readAll(p));
        }
    }

    private static String execCapture(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.waitFor(2, TimeUnit.MINUTES);
        return readAll(p);
    }

    private static String readAll(Process p) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("SPRING_PROFILES_ACTIVE", () -> "test");
        registry.add("DB_URL", () -> mysqlUrl);
        registry.add("DB_USERNAME", () -> mysqlUsername);
        registry.add("DB_PASSWORD", () -> mysqlPassword);
        registry.add("REDIS_HOST", () -> redisHost);
        registry.add("REDIS_PORT", () -> String.valueOf(redisPort));
    }

    /** 生成不冲突的测试用户名（唯一索引约束） */
    protected static String uniqueName(String prefix) {
        return prefix + System.nanoTime();
    }

    protected static final String REGISTER_CODE = "886688";

    /** 注册并登录一个测试用户，返回用户 ID */
    protected Long registerUser(String username,
                                com.linkx.server.service.SysUserService sysUserService,
                                org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        String email = username + "@linkx-it.local";
        redisTemplate.opsForValue().set(
                "linkx:register-email:" + email, REGISTER_CODE, Duration.ofMinutes(10));
        com.linkx.server.controller.dto.RegisterDTO dto = new com.linkx.server.controller.dto.RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(TEST_PASSWORD);
        dto.setNickname("测试-" + username);
        dto.setEmail(email);
        dto.setEmailCode(REGISTER_CODE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        sysUserService.register(dto, request);
        com.linkx.server.controller.dto.LoginDTO loginDTO = new com.linkx.server.controller.dto.LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(TEST_PASSWORD);
        com.linkx.server.controller.vo.TokenVO tokenVO =
                sysUserService.login(loginDTO, "127.0.0.1", "LinkX-IT/1.0", request);
        return tokenVO.getUser().getId();
    }

}
