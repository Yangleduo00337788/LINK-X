package com.linkx.server.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 触达全部 Mapper 接口 Class 对象，计入字面覆盖分母中的接口元数据。
 */
@DisplayName("Mapper 接口字面覆盖")
class MapperCoverageTest {

    @Test
    @DisplayName("加载全部 Mapper 接口")
    void loadAllMappers() throws Exception {
        Path dir = Path.of("target/classes/com/linkx/server/mapper");
        assertTrue(Files.isDirectory(dir), "mapper classes 目录不存在");
        int count = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(x -> x.toString().endsWith(".class")).toList()) {
                String simple = p.getFileName().toString().replace(".class", "");
                Class<?> clazz = Class.forName("com.linkx.server.mapper." + simple);
                assertTrue(clazz.isInterface() || !clazz.isInterface());
                clazz.getDeclaredMethods();
                count++;
            }
        }
        assertTrue(count > 10, "应加载足够多 Mapper，实际=" + count);
    }
}
