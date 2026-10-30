package com.tirocinioaltrama.slmorchestrator.config.ollama;

import java.net.http.HttpClient;

import com.tirocinioaltrama.slmorchestrator.config.OrchestratorLoggingProperties;
import com.tirocinioaltrama.slmorchestrator.impl.model.OllamaRuntimeOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.impl.model.OllamaModelClient;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public ModelClient modelClient(
            RestClient ollamaRestClient,
            OllamaProperties properties,
            OrchestratorLoggingProperties loggingProperties
    ) {
        boolean includeSensitive = loggingProperties != null && loggingProperties.includeSensitiveValues();
        OllamaRuntimeOptions runtimeOptions =
                new OllamaRuntimeOptions(
                        properties.keepAlive(),
                        properties.numCtx(),
                        properties.repeatPenalty(),
                        properties.numThread(),
                        properties.numPredict(),
                        properties.temperature(),
                        properties.seed()
                );
        return new OllamaModelClient(ollamaRestClient, properties.model(), includeSensitive, runtimeOptions);
    }
}