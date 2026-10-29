package com.tirocinioaltrama.llmorchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.tirocinioaltrama.llmorchestrator.config.OrchestratorLoggingProperties;

@SpringBootApplication
@EnableConfigurationProperties(OrchestratorLoggingProperties.class)
public class LlmorchestratorApplication {

    private static final Logger log = LoggerFactory.getLogger(LlmorchestratorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LlmorchestratorApplication.class, args);
    }

    @Bean
    public CommandLineRunner logSensitiveValuesWarning(OrchestratorLoggingProperties loggingProperties) {
        return args -> {
            if (loggingProperties.includeSensitiveValues()) {
                log.warn("ATTENZIONE: 'orchestrator.logging.include-sensitive-values' è abilitato. I valori sensibili dell'input e dei campi rifiutati verranno inclusi nei log.");
            }
        };
    }
}

