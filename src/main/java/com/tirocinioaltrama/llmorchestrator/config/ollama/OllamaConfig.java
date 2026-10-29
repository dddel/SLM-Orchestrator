package com.tirocinioaltrama.llmorchestrator.config.ollama;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.tirocinioaltrama.llmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.llmorchestrator.impl.model.OllamaModelClient;

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
            com.tirocinioaltrama.llmorchestrator.config.OrchestratorLoggingProperties loggingProperties
    ) {
        boolean includeSensitive = loggingProperties != null && loggingProperties.includeSensitiveValues();
        com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions runtimeOptions =
                new com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions(
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