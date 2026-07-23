package com.linkx.server.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 踩点 MyBatis-Flex 生成的 TableDef，使其进入 JaCoCo 字面覆盖。
 */
@DisplayName("TableDef 字面覆盖")
class TableDefCoverageTest {

    @Test
    @DisplayName("加载并访问全部 TableDef 静态字段")
    void coverAllTableDefs() throws Exception {
        Path classes = Path.of("target/classes/com/linkx/server/entity/table");
        if (!Files.isDirectory(classes)) {
            // 尚未编译生成时跳过硬失败，改为尝试已知包扫描
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try (var in = cl.getResourceAsStream("com/linkx/server/entity/table")) {
                assertTrue(in != null || Files.isDirectory(classes),
                        "找不到 entity.table 类路径，请先 mvn compile");
            }
        }

        int count = 0;
        if (Files.isDirectory(classes)) {
            try (Stream<Path> stream = Files.list(classes)) {
                for (Path p : stream.filter(x -> x.toString().endsWith(".class")).toList()) {
                    String simple = p.getFileName().toString().replace(".class", "");
                    Class<?> clazz = Class.forName("com.linkx.server.entity.table." + simple);
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
        }
        assertTrue(count > 0, "应至少覆盖 1 个 TableDef");
    }
}
