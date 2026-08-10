package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 校验 classpath 是否包含 application.yml 等资源。
 * <p>
 * IDEA 增量编译有时只输出 .class 到 target/classes，不复制 src/main/resources，
 * 会导致 context-path 变为 /、linkx.jwt.secret 等占位符无法解析。本地开发时自动同步一次。
 * </p>
 */
public final class ClasspathConfigVerifier {

    private ClasspathConfigVerifier() {
    }

    public static void ensureResourcesPresent(Class<?> anchor) {
        if (hasResource(anchor, "application.yml")) {
            return;
        }
        if (isRunningFromJar(anchor)) {
            failMissingConfig();
        }
        Path moduleDir = resolveModuleDir();
        if (moduleDir == null) {
            failMissingConfig();
        }
        Path source = moduleDir.resolve("src/main/resources");
        Path target = moduleDir.resolve("target/classes");
        if (!Files.isDirectory(source)) {
            failMissingConfig();
        }
        try {
            Files.createDirectories(target);
            syncResources(source, target);
            System.out.println("[LinkX] synced resources: "
                    + source.toAbsolutePath() + " → " + target.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[LinkX] WARN resource sync failed: " + e.getMessage());
            failMissingConfig();
        }
        if (!hasResource(anchor, "application.yml")) {
            failMissingConfig();
        }
    }

    private static boolean hasResource(Class<?> anchor, String name) {
        try (InputStream in = anchor.getClassLoader().getResourceAsStream(name)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isRunningFromJar(Class<?> anchor) {
        var url = anchor.getProtectionDomain().getCodeSource().getLocation();
        return url != null && url.getPath().endsWith(".jar");
    }

    private static Path resolveModuleDir() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path dir : new Path[]{cwd, cwd.resolve("linkx-server")}) {
            if (Files.isDirectory(dir.resolve("src/main/resources"))
                    && Files.isDirectory(dir.resolve("src/main/java"))) {
                return dir;
            }
        }
        return null;
    }

    private static void syncResources(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void failMissingConfig() {
        System.err.println("""
                [LinkX] FATAL: classpath 中缺少 application.yml。
                常见原因：IDEA 只做了增量 Java 编译，未将 src/main/resources 复制到 target/classes。
                修复方式（任选其一）：
                  1. 在 linkx-server 目录执行: mvn compile
                  2. IDEA: Build → Rebuild Project
                  3. IDEA: Settings → Build Tools → Maven → 勾选 Delegate IDE build/run actions to Maven
                """);
        System.exit(1);
    }
}
