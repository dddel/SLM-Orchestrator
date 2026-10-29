package com.tirocinioaltrama.llmorchestrator.config.ollama;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.YamlPipelineLoader;
import com.tirocinioaltrama.llmorchestrator.impl.router.LocalOllamaRouter;

class OllamaServerInitializerTest {

    private TestLogAppender appender;
    private org.apache.logging.log4j.core.Logger log4jLogger;
    private HttpServer testServer;

    static class TestLogAppender extends AbstractAppender {
        private final List<String> warnMessages = new ArrayList<>();

        protected TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel() == Level.WARN) {
                warnMessages.add(event.getMessage().getFormattedMessage());
            }
        }

        public List<String> getWarnMessages() {
            return warnMessages;
        }

        public void clear() {
            warnMessages.clear();
        }
    }

    @BeforeEach
    void setUp() {
        appender = new TestLogAppender("OllamaInitTestLogAppender");
        appender.start();
        log4jLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(OllamaServerInitializer.class);
        log4jLogger.addAppender(appender);
        log4jLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        if (appender != null && log4jLogger != null) {
            appender.stop();
            log4jLogger.removeAppender(appender);
        }
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    private YamlPipelineLoader createPipelineLoader() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ollama.model", "qwen2.5-generic:latest");
        env.setProperty("ollama.email-model", "qwen2.5-email:latest");
        env.setProperty("ollama.invoice-model", "qwen2.5-fattura:latest");
        env.setProperty("ollama.anagrafica-model", "qwen2.5-anagrafica:latest");
        ValidatorRegistry vr = new ValidatorRegistry();
        PipelineConfigValidator cv = new PipelineConfigValidator(vr, new com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry(), new org.springframework.core.io.DefaultResourceLoader());
        return new YamlPipelineLoader(env, vr, cv);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<YamlPipelineLoader> mockLoaderProvider(YamlPipelineLoader loader) {
        ObjectProvider<YamlPipelineLoader> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(loader);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LocalOllamaRouter> mockRouterProvider(LocalOllamaRouter router) {
        ObjectProvider<LocalOllamaRouter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(router);
        return provider;
    }

    @Test
    @DisplayName("C3: Normalizzazione nomi modelli con e senza :latest")
    void testModelNormalizationLogic() {
        assertTrue(OllamaServerInitializer.matchesModel("qwen2.5-generic", "qwen2.5-generic:latest"));
        assertTrue(OllamaServerInitializer.matchesModel("qwen2.5-generic:latest", "qwen2.5-generic:latest"));
        assertTrue(OllamaServerInitializer.matchesModel("qwen2.5-generic:latest", "qwen2.5-generic"));
        assertTrue(OllamaServerInitializer.matchesModel("qwen2.5:3b-instruct", "qwen2.5:3b-instruct"));
        assertFalse(OllamaServerInitializer.matchesModel("qwen2.5:7b", "qwen2.5:3b"));
        assertFalse(OllamaServerInitializer.matchesModel("unknown-model", "qwen2.5:latest"));
    }

    @Test
    @DisplayName("C3: Se tutti i modelli sono presenti nel demone Ollama, nessun WARN viene emesso")
    void testVerifyModelsAllPresentNoWarnings() throws IOException {
        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        String jsonResponse = """
                {
                  "models": [
                    {"name": "qwen2.5-generic:latest"},
                    {"name": "qwen2.5-email:latest"},
                    {"name": "qwen2.5-fattura:latest"},
                    {"name": "qwen2.5-anagrafica:latest"},
                    {"name": "qwen2.5-router:latest"}
                  ]
                }
                """;
        testServer.createContext("/api/tags", exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        testServer.start();

        int port = testServer.getAddress().getPort();
        OllamaProperties props = new OllamaProperties("http://localhost:" + port, "qwen2.5-generic:latest", null, null, null, null, null);
        LocalOllamaRouter router = new LocalOllamaRouter("http://localhost:" + port, "qwen2.5-router:latest", null);

        OllamaServerInitializer initializer = new OllamaServerInitializer(
                props,
                mockLoaderProvider(createPipelineLoader()),
                mockRouterProvider(router),
                new ObjectMapper()
        );

        initializer.verifyModels();

        assertTrue(appender.getWarnMessages().isEmpty(),
                "Nessun WARN deve essere emesso quando tutti i modelli sono presenti. Trovati: " + appender.getWarnMessages());
    }

    @Test
    @DisplayName("C3: Se un modello è mancante, viene emesso il WARN corrispondente")
    void testVerifyModelsMissingModelEmitsWarning() throws IOException {
        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        // Manca qwen2.5-email:latest
        String jsonResponse = """
                {
                  "models": [
                    {"name": "qwen2.5-generic:latest"},
                    {"name": "qwen2.5-fattura:latest"},
                    {"name": "qwen2.5-anagrafica:latest"},
                    {"name": "qwen2.5-router:latest"}
                  ]
                }
                """;
        testServer.createContext("/api/tags", exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        testServer.start();

        int port = testServer.getAddress().getPort();
        OllamaProperties props = new OllamaProperties("http://localhost:" + port, "qwen2.5-generic:latest", null, null, null, null, null);
        LocalOllamaRouter router = new LocalOllamaRouter("http://localhost:" + port, "qwen2.5-router:latest", null);

        OllamaServerInitializer initializer = new OllamaServerInitializer(
                props,
                mockLoaderProvider(createPipelineLoader()),
                mockRouterProvider(router),
                new ObjectMapper()
        );

        initializer.verifyModels();

        List<String> warns = appender.getWarnMessages();
        boolean foundMissingEmailWarn = warns.stream()
                .anyMatch(msg -> msg.contains("qwen2.5-email:latest") && msg.contains("non trovato nel demone Ollama"));
        assertTrue(foundMissingEmailWarn,
                "Deve essere emesso un WARN che segnala il modello mancante 'qwen2.5-email:latest'. Trovati: " + warns);
    }

    @Test
    @DisplayName("C3: Se Ollama non è raggiungibile, viene emesso un WARN sintetico e non viene sollevata eccezione")
    void testVerifyModelsOllamaUnreachableEmitsWarningAndDoesNotThrow() throws IOException {
        // Troviamo una porta libera e non apriamo alcun server
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }

        OllamaProperties props = new OllamaProperties("http://localhost:" + unusedPort, "qwen2.5-generic:latest", null, null, null, null, null);
        LocalOllamaRouter router = new LocalOllamaRouter("http://localhost:" + unusedPort, "qwen2.5-router:latest", null);

        OllamaServerInitializer initializer = new OllamaServerInitializer(
                props,
                mockLoaderProvider(createPipelineLoader()),
                mockRouterProvider(router),
                new ObjectMapper()
        );

        assertDoesNotThrow(() -> initializer.verifyModels(),
                "Non deve essere sollevata eccezione quando Ollama non è raggiungibile");

        List<String> warns = appender.getWarnMessages();
        boolean foundUnreachableWarn = warns.stream()
                .anyMatch(msg -> msg.contains("Impossibile verificare") && msg.contains("non raggiungibile"));
        assertTrue(foundUnreachableWarn,
                "Deve essere emesso un WARN sintetico di mancata verifica. Trovati: " + warns);
    }
}
