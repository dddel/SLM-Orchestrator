package com.tirocinioaltrama.slmorchestrator.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouterRegistryTest {

    @Test
    @DisplayName("Operazioni di base con RouterType strutturato")
    void testRegistryOperations() {
        Router mockOllama = mock(Router.class);

        RouterRegistry registry = new RouterRegistry(
                Map.of(RouterType.LOCAL_OLLAMA, mockOllama),
                RouterType.LOCAL_OLLAMA
        );

        assertEquals(RouterType.LOCAL_OLLAMA, registry.getDefaultRouterType());
        assertEquals("local-ollama", registry.getDefaultRouterName());
        assertEquals(mockOllama, registry.getDefaultRouter());
        assertTrue(registry.get(RouterType.LOCAL_OLLAMA).isPresent());
        assertTrue(registry.get("local-ollama").isPresent());
        assertEquals(1, registry.getAll().size());

        // Resolve
        assertEquals(mockOllama, registry.resolve(RouterType.LOCAL_OLLAMA));
        assertEquals(mockOllama, registry.resolve("local-ollama"));
        assertEquals(mockOllama, registry.resolve((RouterType) null)); // fallback con null
        assertEquals(mockOllama, registry.resolve((String) null)); // fallback con null string

        // Ignora valori nulli
        registry.register((RouterType) null, mockOllama);
        registry.register(RouterType.LOCAL_OLLAMA, null);
        registry.setDefaultRouterType(null);
        registry.setDefaultRouterName(null);
        assertEquals(RouterType.LOCAL_OLLAMA, registry.getDefaultRouterType());
    }

    @Test
    @DisplayName("Fallback su registry vuoto")
    void testEmptyRegistryFallback() {
        RouterRegistry empty = new RouterRegistry();
        assertNull(empty.getDefaultRouter());
        assertNull(empty.resolve(RouterType.LOCAL_OLLAMA));
        assertNull(empty.resolve("local-ollama"));

        Router mockRouter = mock(Router.class);
        empty.register(RouterType.LOCAL_OLLAMA, mockRouter);
        assertEquals(mockRouter, empty.getDefaultRouter());
    }

    @Test
    @DisplayName("Registrazione ed estensibilità con router custom basati su stringa e classe Router")
    void testCustomRouterRegistrationAndResolution() {
        RouterRegistry registry = new RouterRegistry();

        Router customLocalRouter = mock(Router.class);
        when(customLocalRouter.getName()).thenReturn("custom-regex-router");

        registry.register(customLocalRouter);

        assertTrue(registry.contains("custom-regex-router"));
        assertTrue(registry.get("custom-regex-router").isPresent());
        assertEquals(customLocalRouter, registry.resolve("custom-regex-router"));
        assertEquals(customLocalRouter, registry.resolve("CUSTOM-REGEX-ROUTER")); // case insensitive

        assertTrue(registry.getRegisteredNames().contains("custom-regex-router"));
        assertEquals(1, registry.getAllByName().size());
    }

    @Test
    @DisplayName("Impostazione router di default personalizzato tramite stringa")
    void testCustomDefaultRouterByName() {
        RouterRegistry registry = new RouterRegistry();

        Router defaultRouter = mock(Router.class);
        when(defaultRouter.getName()).thenReturn("heuristic-default");
        Router secondaryRouter = mock(Router.class);
        when(secondaryRouter.getName()).thenReturn("secondary");

        registry.register(defaultRouter);
        registry.register(secondaryRouter);

        registry.setDefaultRouterName("secondary");
        assertEquals("secondary", registry.getDefaultRouterName());
        assertEquals(secondaryRouter, registry.getDefaultRouter());

        // Risoluzione con nome non riconosciuto usa il fallback sul default
        assertEquals(secondaryRouter, registry.resolve("unknown-router-name"));
    }
}
