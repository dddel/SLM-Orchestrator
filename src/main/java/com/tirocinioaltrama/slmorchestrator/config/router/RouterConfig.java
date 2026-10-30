package com.tirocinioaltrama.slmorchestrator.config.router;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

import com.tirocinioaltrama.slmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.slmorchestrator.core.Router;
import com.tirocinioaltrama.slmorchestrator.core.RouterRegistry;
import com.tirocinioaltrama.slmorchestrator.core.RouterType;
import com.tirocinioaltrama.slmorchestrator.impl.model.OllamaRuntimeOptions;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.SystemPromptResolver;
import com.tirocinioaltrama.slmorchestrator.impl.router.ApusReflexRouter;
import com.tirocinioaltrama.slmorchestrator.impl.router.LocalOllamaRouter;

@Configuration
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);

    @Bean
    public LocalOllamaRouter localOllamaRouter(
            OllamaProperties ollamaProperties,
            ResourceLoader resourceLoader
    ) {
        String model = ollamaProperties.routerModel();
        log.info("Inizializzazione Router Local Ollama (modello: {})", model);
        String systemPrompt = null;
        if (ollamaProperties.routerSystemPrompt() != null && !ollamaProperties.routerSystemPrompt().isBlank()) {
            try {
                systemPrompt = SystemPromptResolver.resolve(
                        ollamaProperties.routerSystemPrompt(),
                        resourceLoader,
                        "router locale Ollama"
                );
            } catch (Exception e) {
                log.warn("Impossibile risolvere il system prompt per il router locale ('{}'): {}",
                        ollamaProperties.routerSystemPrompt(), e.getMessage());
            }
        }
        OllamaRuntimeOptions runtimeOptions =
                new OllamaRuntimeOptions(
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
    public ApusReflexRouter apusReflexRouter(
            OllamaProperties ollamaProperties
    ) {
        String model = ollamaProperties.reflexModel();
        log.info("Inizializzazione Router APUS Reflex (modello: {})", model);
        return new ApusReflexRouter(
                ollamaProperties.baseUrl(),
                model,
                ollamaProperties.readTimeout()
        );
    }

    @Bean
    public RouterRegistry routerRegistry(
            LocalOllamaRouter localOllamaRouter,
            ApusReflexRouter apusReflexRouter,
            @Value("${router.type:local-ollama}") String configuredDefaultType
    ) {
        RouterRegistry registry = new RouterRegistry();
        registry.register(localOllamaRouter);
        registry.register(RouterType.REFLEX, apusReflexRouter);
        registry.register("reflex", apusReflexRouter);
        registry.register("apus-reflex", apusReflexRouter);

        RouterType defaultType = RouterType.fromString(configuredDefaultType);
        if (defaultType != null) {
            registry.setDefaultRouterType(defaultType);
        } else if (configuredDefaultType != null && !configuredDefaultType.isBlank()) {
            registry.setDefaultRouterName(configuredDefaultType);
        } else {
            registry.setDefaultRouterType(RouterType.LOCAL_OLLAMA);
        }
        log.info("RouterRegistry configurato con router di default: {}", configuredDefaultType);
        return registry;
    }

    /**
     * Auto-registrazione dei router custom o estensioni definite nel contesto applicativo.
     */
    @Bean
    public SmartInitializingSingleton autoRegisterRouters(
            ApplicationContext applicationContext,
            RouterRegistry routerRegistry
    ) {
        return () -> {
            Map<String, Router> routerBeans = applicationContext.getBeansOfType(Router.class);
            for (Map.Entry<String, Router> entry : routerBeans.entrySet()) {
                String beanName = entry.getKey();
                Router router = entry.getValue();
                if (router != null && !"primaryRouter".equals(beanName)) {
                    routerRegistry.register(router);
                }
            }
        };
    }

    @Bean
    @Primary
    public Router primaryRouter(RouterRegistry routerRegistry) {
        return routerRegistry.getDefaultRouter();
    }
}
