package com.tirocinioaltrama.llmorchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Configurazione OpenAPI 3 / Swagger UI per l'orchestratore neuro-simbolico.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI llmOrchestratorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LLM Orchestrator API - Catena SLM Neuro-Simbolica")
                        .description("Microservizio di orchestrazione a pipeline per Small Language Models (1-4B) locali su Ollama. " +
                                "Include routing dinamico, decoding vincolato con JSON Schema, validatori deterministici " +
                                "(Codice Fiscale con omocodia, P.IVA, IBAN, RFC 5322 Email), retry con feedback ed escalation ordinata.")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact()
                                .name("Tirocinio Altrama")
                                .url("https://github.com/tirocinioaltrama/llmorchestrator"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
