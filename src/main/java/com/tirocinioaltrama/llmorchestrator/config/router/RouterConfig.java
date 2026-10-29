package com.tirocinioaltrama.llmorchestrator.config.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.tirocinioaltrama.llmorchestrator.core.Router;
import com.tirocinioaltrama.llmorchestrator.core.RouterRegistry;
import com.tirocinioaltrama.llmorchestrator.core.RouterType;
import com.tirocinioaltrama.llmorchestrator.impl.router.GeminiRouter;
import com.tirocinioaltrama.llmorchestrator.impl.router.JevRouter;
import com.tirocinioaltrama.llmorchestrator.impl.router.LocalOllamaRouter;

@Configuration
@EnableConfigurationProperties({GeminiProperties.class, JevProperties.class})
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);

    @Bean
    public GeminiRouter geminiRouter(GeminiProperties geminiProperties) {
        log.info("Inizializzazione Router Gemini (modello: {})", geminiProperties.model());
        return new GeminiRouter(geminiProperties);
    }

    @Bean
    public JevRouter jevRouter(JevProperties jevProperties) {
        log.info("Inizializzazione Router Jev/Cloudflare (provider: {}, modello: {})",
                jevProperties.provider(), jevProperties.model());
        return new JevRouter(jevProperties);
    }

    @Bean
    public LocalOllamaRouter localOllamaRouter(
            com.tirocinioaltrama.llmorchestrator.config.ollama.OllamaProperties ollamaProperties,
            org.springframework.core.io.ResourceLoader resourceLoader
    ) {
        String model = ollamaProperties.routerModel();
        log.info("Inizializzazione Router Local Ollama (modello: {})", model);
        String systemPrompt = null;
        if (ollamaProperties.routerSystemPrompt() != null && !ollamaProperties.routerSystemPrompt().isBlank()) {
            try {
                systemPrompt = com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.SystemPromptResolver.resolve(
                        ollamaProperties.routerSystemPrompt(),
                        resourceLoader,
                        "router locale Ollama"
                );
            } catch (Exception e) {
                log.warn("Impossibile risolvere il system prompt per il router locale ('{}'): {}",
                        ollamaProperties.routerSystemPrompt(), e.getMessage());
            }
        }
        com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions runtimeOptions =
                new com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions(
                        ollamaProperties.keepAlive(),
                        ollamaProperties.numCtx(),
                        ollamaProperties.repeatPenalty(),
                        ollamaProperties.numThread(),
                        ollamaProperties.numPredict(),
                        ollamaProperties.temperature(),
                        ollamaProperties.seed()
                );
        return new LocalOllamaRouter(
                ollamaProperties.baseUrl(),
                model,
                ollamaProperties.readTimeout(),
                systemPrompt,
                runtimeOptions
        );
    }

    @Bean
    public RouterRegistry routerRegistry(
            GeminiRouter geminiRouter,
            JevRouter jevRouter,
            LocalOllamaRouter localOllamaRouter,
            @Value("${router.type:gemini}") String configuredDefaultType
    ) {
        RouterRegistry registry = new RouterRegistry();
        registry.register(RouterType.GEMINI, geminiRouter);
        registry.register(RouterType.JEV, jevRouter);
        registry.register(RouterType.LOCAL_OLLAMA, localOllamaRouter);

        RouterType defaultType = RouterType.fromString(configuredDefaultType);
        if (defaultType == null) {
            defaultType = RouterType.GEMINI;
        }
        registry.setDefaultRouterType(defaultType);
        log.info("RouterRegistry configurato con router di default: {}", defaultType);
        return registry;
    }

    @Bean
    @Primary
    public Router primaryRouter(RouterRegistry routerRegistry) {
        return routerRegistry.getDefaultRouter();
    }
}
