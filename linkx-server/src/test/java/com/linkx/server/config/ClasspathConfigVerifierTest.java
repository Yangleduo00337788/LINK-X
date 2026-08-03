package com.linkx.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClasspathConfigVerifierTest {

    @Test
    @EnabledIf("hasCompiledResources")
    void ensureResourcesPresent_restoresMissingApplicationYml() throws Exception {
        Path appYml = Path.of("target/classes/application.yml");
        assumeTrue(Files.exists(appYml), "run mvn compile first");

        byte[] backup = Files.readAllBytes(appYml);
        try {
            Files.delete(appYml);
            ClasspathConfigVerifier.ensureResourcesPresent(ClasspathConfigVerifierTest.class);
            assertTrue(Files.exists(appYml));
            assertTrue(Files.size(appYml) > 0);
        } finally {
            Files.write(appYml, backup);
        }
    }

    @SuppressWarnings("unused")
    static boolean hasCompiledResources() {
        return Files.exists(Path.of("target/classes/application.yml"));
    }
}
