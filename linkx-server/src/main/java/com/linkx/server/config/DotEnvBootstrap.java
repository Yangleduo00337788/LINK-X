package com.linkx.server.config;

/**
 * 在 SpringApplication.run 之前把 .env.* 写入 System properties，
 * 保证 IDEA / java -jar 都能解析 application.yml 里的 ${VAR}。
 * <p>
 * 已存在的环境变量 / -D 系统属性不会被覆盖。
 * </p>
 */
public final class DotEnvBootstrap {

    private DotEnvBootstrap() {
    }

    public static void load() {
        DotEnvLoader.Result result = DotEnvLoader.load();
        if (!result.found()) {
            System.err.println("[LinkX] WARN " + result.message());
            return;
        }
        int applied = 0;
        for (var e : result.values().entrySet()) {
            String key = e.getKey();
            // OS 环境变量优先
            if (System.getenv(key) != null) {
                continue;
            }
            // 已有 -D 不覆盖
            if (System.getProperty(key) != null) {
                continue;
            }
            System.setProperty(key, e.getValue() != null ? e.getValue() : "");
            applied++;
        }
        // 同步 Spring profile，避免落到 default
        String profile = result.values().getOrDefault("SPRING_PROFILES_ACTIVE", result.profile());
        if (profile != null && !profile.isBlank()) {
            if (System.getenv("SPRING_PROFILES_ACTIVE") == null
                    && System.getProperty("spring.profiles.active") == null) {
                System.setProperty("spring.profiles.active", profile.split(",")[0].trim());
            }
        }
        System.out.println("[LinkX] loaded env file: " + result.file().toAbsolutePath()
                + " (profile=" + profile + ", applied=" + applied + "/" + result.values().size() + ")");
    }
}
