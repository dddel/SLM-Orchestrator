package com.tirocinioaltrama.llmorchestrator.impl.pipeline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tirocinioaltrama.llmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineRegistry;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.YamlPipelineLoader;

/**
 * Configurazione Spring per la creazione e registrazione delle pipeline di dominio
 * caricate dichiarativamente dai file di configurazione YAML.
 */
@Configuration
public class PipelineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfiguration.class);

    private final YamlPipelineLoader yamlPipelineLoader;

    public PipelineConfiguration(YamlPipelineLoader yamlPipelineLoader) {
        this.yamlPipelineLoader = yamlPipelineLoader;
    }

    /**
     * Carica tutte le pipeline definite nei file YAML e le espone come lista di Bean per il PipelineRegistry.
     */
    @Bean
    public List<Pipeline> configuredPipelines(ModelClient modelClient) {
        log.info("Inizializzazione pipeline dichiarative tramite YamlPipelineLoader...");
        List<Pipeline> pipelines = yamlPipelineLoader.loadPipelines(modelClient);
        log.info("Caricate con successo {} pipeline da configurazione YAML.", pipelines.size());
        return pipelines;
    }

    /**
     * Registra il registro centrale delle pipeline come bean Spring gestito.
     */
    @Bean
    public PipelineRegistry pipelineRegistry(List<Pipeline> configuredPipelines) {
        return new PipelineRegistry(configuredPipelines);
    }
}
