package com.linkx.server.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从分版本 .env 文件加载键值。
 * <p>
 * 供 {@link DotEnvBootstrap}（main 最早调用）与
 * {@link DotEnvEnvironmentPostProcessor} 共用。
 * </p>
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    /**
     * @return 加载结果；未找到文件时 file 为 null、map 为空
     */
    public static Result load() {
        Path envDir = resolveEnvDirectory();
        if (envDir == null) {
            return Result.notFound();
        }
        String profile = resolveProfile(envDir);
        Path profileFile = envDir.resolve(".env." + profile);
        if (!Files.isRegularFile(profileFile)) {
            return Result.missingFile(profile, profileFile);
        }
        Map<String, String> map = parseEnvFile(profileFile);
        return Result.ok(profile, profileFile, map);
    }

    private static String resolveProfile(Path envDir) {
        String fromEnv = firstNonBlank(
                System.getenv("SPRING_PROFILES_ACTIVE"),
                System.getProperty("spring.profiles.active"),
                System.getProperty("SPRING_PROFILES_ACTIVE")
        );
        if (fromEnv != null) {
            return fromEnv.split(",")[0].trim();
        }
        for (String name : List.of(".env.local", ".env.prod")) {
            Path p = envDir.resolve(name);
            if (!Files.isRegularFile(p)) {
                continue;
            }
            String peeked = parseEnvFile(p).get("SPRING_PROFILES_ACTIVE");
            if (peeked != null && !peeked.isBlank()) {
                return peeked.split(",")[0].trim();
            }
        }
        return "local";
    }

    private static Path resolveEnvDirectory() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] dirs = {
                cwd,
                cwd.resolve("linkx-server"),
                cwd.getParent()
        };
        for (Path dir : dirs) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            if (Files.isRegularFile(dir.resolve(".env.local"))
                    || Files.isRegularFile(dir.resolve(".env.prod"))) {
                return dir;
            }
        }
        return null;
    }

    static Map<String, String> parseEnvFile(Path file) {
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = stripQuotes(trimmed.substring(eq + 1).trim());
                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        } catch (IOException ignored) {
            // 忽略不可读
        }
        return map;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    public record Result(boolean found, String profile, Path file, Map<String, String> values, String message) {
        static Result ok(String profile, Path file, Map<String, String> values) {
            return new Result(true, profile, file, values, null);
        }

        static Result notFound() {
            return new Result(false, null, null, Map.of(),
                    "未找到 .env.local / .env.prod（已查 user.dir、linkx-server/、上一级）");
        }

        static Result missingFile(String profile, Path expected) {
            return new Result(false, profile, expected, Map.of(),
                    "profile=" + profile + " 但文件不存在: " + expected.toAbsolutePath());
        }
    }
}
