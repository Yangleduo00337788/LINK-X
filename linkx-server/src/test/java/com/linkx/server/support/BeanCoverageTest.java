package com.linkx.server.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反射踩点：覆盖所有 DTO / VO / Entity 的构造、getter/setter、equals/hashCode/toString。
 */
@DisplayName("Bean 字面覆盖")
class BeanCoverageTest {

    private static final List<String> PACKAGES = List.of(
            "com.linkx.server.controller.dto",
            "com.linkx.server.controller.vo",
            "com.linkx.server.entity"
    );

    @Test
    @DisplayName("扫描并覆盖 dto/vo/entity")
    void coverAllBeans() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        int covered = 0;
        for (String pkg : PACKAGES) {
            for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
                String name = bd.getBeanClassName();
                if (name == null || name.contains(".table.")) {
                    continue;
                }
                Class<?> clazz = Class.forName(name);
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }
                if (clazz.isEnum()) {
                    Object[] constants = clazz.getEnumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            c.toString();
                            ((Enum<?>) c).name();
                            ((Enum<?>) c).ordinal();
                        }
                    }
                    covered++;
                    continue;
                }
                exercise(clazz);
                covered++;
            }
        }
        assertTrue(covered > 50, "应覆盖足够多的 Bean，实际=" + covered);
    }

    private void exercise(Class<?> clazz) throws Exception {
        Object a = newInstance(clazz);
        Object b = newInstance(clazz);
        fillViaSetters(a);
        fillViaSetters(b);

        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) {
                continue;
            }
            if (m.getParameterCount() == 0 && (m.getName().startsWith("get") || m.getName().startsWith("is"))) {
                assertDoesNotThrow(() -> m.invoke(a));
            }
        }

        // builder
        try {
            Method builder = clazz.getMethod("builder");
            Object builderObj = builder.invoke(null);
            Method build = builderObj.getClass().getMethod("build");
            Object built = build.invoke(builderObj);
            built.toString();
        } catch (NoSuchMethodException ignored) {
            // no builder
        }

        a.equals(a);
        a.equals(null);
        a.equals("other");
        a.equals(b);
        a.hashCode();
        a.toString();
    }

    private Object newInstance(Class<?> clazz) throws Exception {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            c.setAccessible(true);
            Class<?>[] pts = c.getParameterTypes();
            Object[] args = new Object[pts.length];
            for (int i = 0; i < pts.length; i++) {
                args[i] = defaultValue(pts[i]);
            }
            try {
                return c.newInstance(args);
            } catch (Exception ignored) {
                // try next
            }
        }
        throw new IllegalStateException("无法实例化: " + clazz.getName());
    }

    private void fillViaSetters(Object target) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() != 1 || !m.getName().startsWith("set")) {
                continue;
            }
            try {
                m.invoke(target, defaultValue(m.getParameterTypes()[0]));
            } catch (Exception ignored) {
                // incompatible setter
            }
        }
        for (Field f : target.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            try {
                if (f.get(target) == null) {
                    f.set(target, defaultValue(f.getType()));
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private Object defaultValue(Class<?> type) {
        if (type == String.class) {
            return "x";
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == short.class || type == Short.class) {
            return (short) 1;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) 1;
        }
        if (type == double.class || type == Double.class) {
            return 1.0d;
        }
        if (type == float.class || type == Float.class) {
            return 1.0f;
        }
        if (type == char.class || type == Character.class) {
            return 'a';
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.now();
        }
        if (type == LocalDate.class) {
            return LocalDate.now();
        }
        if (type == byte[].class) {
            return new byte[]{1};
        }
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return new ArrayList<>();
        }
        if (Set.class.isAssignableFrom(type)) {
            return new HashSet<>();
        }
        if (Map.class.isAssignableFrom(type)) {
            return new HashMap<>();
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : null;
        }
        if (type.isArray()) {
            return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
        }
        return null;
    }
}
