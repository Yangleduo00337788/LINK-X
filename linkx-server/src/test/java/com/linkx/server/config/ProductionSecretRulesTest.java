package com.linkx.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("生产密钥弱值规则")
class ProductionSecretRulesTest {

    @Test
    @DisplayName("空值与过短视为弱")
    void blankAndShortAreWeak() {
        assertTrue(ProductionSecretRules.isBlank(null));
        assertTrue(ProductionSecretRules.isBlank("  "));
        assertTrue(ProductionSecretRules.isWeakSecret(null, 8));
        assertTrue(ProductionSecretRules.isWeakSecret("short", 8));
    }

    @Test
    @DisplayName("常见占位符视为弱")
    void commonPlaceholdersAreWeak() {
        assertTrue(ProductionSecretRules.isWeakSecret("changeme", 8));
        assertTrue(ProductionSecretRules.isWeakSecret("Password", 8));
        assertTrue(ProductionSecretRules.isWeakSecret("minioadmin", 8));
        assertTrue(ProductionSecretRules.isWeakSecret("aaaaaaaa", 8));
    }

    @Test
    @DisplayName("足够长的随机串视为强")
    void strongSecretPasses() {
        assertFalse(ProductionSecretRules.isWeakSecret("xK9mP2vQ7nR4wZ5jB3cL8dF1gH6", 16));
        assertFalse(ProductionSecretRules.isWeakSecret("Test-Local-JWT-Key-For-Integration-2026!!", 32));
    }
}
