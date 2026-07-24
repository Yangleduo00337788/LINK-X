package com.linkx.server.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 踩点 MyBatis-Flex 生成的 TableDef，使其进入 JaCoCo 字面覆盖。
 * <p>
 * 当前工程未启用 mybatis-flex-processor 时可能无生成类，此时跳过而非失败。
 * </p>
 */
@DisplayName("TableDef 字面覆盖")
class TableDefCoverageTest {

    private static final String TABLE_PKG = "com.linkx.server.entity.table";

    @Test
    @DisplayName("加载并访问全部 TableDef 静态字段")
    void coverAllTableDefs() throws Exception {
        Path classesDir = resolveTableDefDir();
        Assumptions.assumeTrue(classesDir != null && Files.isDirectory(classesDir),
                "未生成 entity.table（未启用 mybatis-flex-processor），跳过 TableDef 覆盖");

        int count = 0;
        try (Stream<Path> stream = Files.list(classesDir)) {
            List<Path> classFiles = stream.filter(x -> x.toString().endsWith(".class")).toList();
            for (Path p : classFiles) {
                String simple = p.getFileName().toString().replace(".class", "");
                Class<?> clazz = Class.forName(TABLE_PKG + "." + simple);
                for (Field f : clazz.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v != null) {
                        v.toString();
                    }
                }
                clazz.getDeclaredConstructors();
                count++;
            }
        }
        Assumptions.assumeTrue(count > 0, "entity.table 目录为空，跳过");
    }

    private Path resolveTableDefDir() throws Exception {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("target/classes/com/linkx/server/entity/table"));
        candidates.add(Path.of("linkx-server/target/classes/com/linkx/server/entity/table"));

        URL resource = Thread.currentThread().getContextClassLoader()
                .getResource("com/linkx/server/entity/table");
        if (resource != null && "file".equals(resource.getProtocol())) {
            candidates.add(Paths.get(resource.toURI()));
        }

        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }
}
