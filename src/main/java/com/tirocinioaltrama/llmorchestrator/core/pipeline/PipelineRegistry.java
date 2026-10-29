package com.tirocinioaltrama.llmorchestrator.core.pipeline;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.tirocinioaltrama.llmorchestrator.core.DocumentType;

/**
 * Registro centrale di tutte le pipeline disponibili nel sistema.
 */
public class PipelineRegistry {

    private final Map<DocumentType, Pipeline> pipelines = new ConcurrentHashMap<>();

    public PipelineRegistry(Collection<Pipeline> availablePipelines) {
        if (availablePipelines != null) {
            for (Pipeline p : availablePipelines) {
                register(p);
            }
        }
    }

    public void register(Pipeline pipeline) {
        if (pipeline != null && pipeline.getSupportedType() != null) {
            pipelines.put(pipeline.getSupportedType(), pipeline);
        }
    }

    public Optional<Pipeline> get(DocumentType type) {
        return Optional.ofNullable(pipelines.get(type));
    }

    public boolean contains(DocumentType type) {
        return pipelines.containsKey(type);
    }

    public Collection<DocumentType> getSupportedTypes() {
        return pipelines.keySet();
    }
}
