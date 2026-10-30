package com.tirocinioaltrama.slmorchestrator.config.ollama;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;
import com.tirocinioaltrama.slmorchestrator.impl.router.LocalOllamaRouter;

/**
 * Inizializzatore eseguito al bootstrap dell'applicazione.
 * <p>
 * Verifica la raggiungibilità del demone Ollama (con eventuale tentativo di avvio locale
 * se inattivo) ed esegue un controllo non bloccante dei modelli necessari per le pipeline
 * e per i router locali, segnalando eventuali assenze con log a livello WARN.
 * <p>
 * Il controllo ha un timeout breve (2 secondi) per non rallentare l'avvio in caso di Ollama spento.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "ollama.init.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OllamaServerInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaServerInitializer.class);
    private static final Duration TAGS_TIMEOUT = Duration.ofSeconds(2);

    private final OllamaProperties properties;
    private final ObjectProvider<YamlPipelineLoader> yamlPipelineLoaderProvider;
    private final ObjectProvider<LocalOllamaRouter> localOllamaRouterProvider;
    private final ObjectMapper objectMapper;

    public OllamaServerInitializer(OllamaProperties properties) {
        this(properties, null, null, new ObjectMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaServerInitializer(
            OllamaProperties properties,
            ObjectProvider<YamlPipelineLoader> yamlPipelineLoaderProvider,
            ObjectProvider<LocalOllamaRouter> localOllamaRouterProvider
    ) {
        this(properties, yamlPipelineLoaderProvider, localOllamaRouterProvider, new ObjectMapper());
    }

    public OllamaServerInitializer(
            OllamaProperties properties,
            ObjectProvider<YamlPipelineLoader> yamlPipelineLoaderProvider,
            ObjectProvider<LocalOllamaRouter> localOllamaRouterProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.yamlPipelineLoaderProvider = yamlPipelineLoaderProvider;
        this.localOllamaRouterProvider = localOllamaRouterProvider;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties != null && Boolean.FALSE.equals(properties.initEnabled())) {
            log.info("Inizializzazione e verifica automatica di Ollama disabilitata da configurazione.");
            return;
        }

        if (isOllamaReachable()) {
            log.info("Server Ollama già attivo e raggiungibile su {}", properties.baseUrl());
            verifyModels();
            return;
        }

        log.warn("Server Ollama non attivo su {}. Tentativo di avvio automatico...", properties.baseUrl());
        startOllamaDaemon();

        boolean ready = false;
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (isOllamaReachable()) {
                ready = true;
                break;
            }
        }

        if (ready) {
            log.info("Server Ollama avviato e pronto all'uso su {}", properties.baseUrl());
            verifyModels();
        } else {
            log.error("Impossibile contattare Ollama dopo il tentativo di avvio. Assicurati che sia installato o avvialo manualmente con 'ollama serve' o 'systemctl start ollama'.");
            log.warn("Impossibile verificare la presenza dei modelli su Ollama ({}): demone non raggiungibile o errore di connessione.", properties.baseUrl());
        }
    }

    /**
     * Raccoglie i modelli richiesti da pipeline e router locali e ne verifica la presenza
     * interrogando l'endpoint /api/tags di Ollama.
     * In caso di mancata risposta o modello assente, emette warning non bloccanti.
     */
    public void verifyModels() {
        Set<String> requiredModels = collectRequiredModels();
        if (requiredModels.isEmpty()) {
            return;
        }

        Set<String> availableTags;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TAGS_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/api/tags"))
                    .timeout(TAGS_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("Impossibile verificare la presenza dei modelli su Ollama ({}): status code HTTP {}.",
                        properties.baseUrl(), resp.statusCode());
                return;
            }

            OllamaTagsResponse tagsResponse = objectMapper.readValue(resp.body(), OllamaTagsResponse.class);
            if (tagsResponse == null || tagsResponse.models() == null) {
                availableTags = Collections.emptySet();
            } else {
                availableTags = tagsResponse.models().stream()
                        .map(ModelTag::name)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("Impossibile verificare la presenza dei modelli su Ollama ({}): demone non raggiungibile o errore di connessione ({}).",
                    properties.baseUrl(), e.getMessage());
            return;
        }

        for (String reqModel : requiredModels) {
            boolean found = availableTags.stream().anyMatch(avail -> matchesModel(reqModel, avail));
            if (!found) {
                log.warn("Modello '{}' non trovato nel demone Ollama: le richieste che lo usano falliranno a runtime", reqModel);
            }
        }
    }

    /**
     * Raccoglie l'insieme dei modelli richiesti da:
     * 0. modello base unificato (Single Base Model) ed eventuale modello di escalation configurato
     * 1. passi model_inference di tutte le pipeline caricate
     * 2. modelli di escalation (escalate_to)
     * 3. router locali basati su Ollama
     */
    public Set<String> collectRequiredModels() {
        Set<String> required = new LinkedHashSet<>();

        if (properties.baseModel() != null && !properties.baseModel().isBlank()) {
            required.add(properties.baseModel().strip());
        }
        if (properties.escalationModel() != null && !properties.escalationModel().isBlank()) {
            required.add(properties.escalationModel().strip());
        }

        if (yamlPipelineLoaderProvider != null) {
            YamlPipelineLoader loader = yamlPipelineLoaderProvider.getIfAvailable();
            if (loader != null) {
                try {
                    List<PipelineDefinition> definitions = loader.loadDefinitions();
                    for (PipelineDefinition def : definitions) {
                        if (def.getSteps() == null) continue;
                        for (StepDefinition step : def.getSteps()) {
                            if ("model_inference".equalsIgnoreCase(step.getType())
                                    && step.getModel() != null
                                    && !step.getModel().isBlank()) {
                                required.add(step.getModel().strip());
                            }
                            if (step.getOnFailureDefinition() != null) {
                                String escalateTo = step.getOnFailureDefinition().getEscalateTo();
                                if (escalateTo != null && !escalateTo.isBlank()) {
                                    required.add(escalateTo.strip());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Impossibile caricare le definizioni delle pipeline per la verifica dei modelli: {}", e.getMessage());
                }
            }
        }

        if (localOllamaRouterProvider != null) {
            LocalOllamaRouter localRouter = localOllamaRouterProvider.getIfAvailable();
            if (localRouter != null && localRouter.getModel() != null && !localRouter.getModel().isBlank()) {
                required.add(localRouter.getModel().strip());
            }
        }


        return required;
    }

    /**
     * Confronta un nome di modello richiesto con un tag disponibile su Ollama.
     * Supporta normalizzazione per i tag con o senza ':latest'.
     */
    public static boolean matchesModel(String requiredModel, String availableTag) {
        if (requiredModel == null || availableTag == null) {
            return false;
        }
        String req = requiredModel.trim().toLowerCase(Locale.ROOT);
        String avail = availableTag.trim().toLowerCase(Locale.ROOT);

        if (req.equals(avail)) {
            return true;
        }
        if (!req.contains(":") && (req + ":latest").equals(avail)) {
            return true;
        }
        if (req.endsWith(":latest") && req.substring(0, req.length() - 7).equals(avail)) {
            return true;
        }
        return false;
    }

    private boolean isOllamaReachable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TAGS_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/api/tags"))
                    .timeout(TAGS_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(request, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void startOllamaDaemon() {
        try {
            // Prova 1: systemctl --user start ollama
            Process process = new ProcessBuilder("systemctl", "--user", "start", "ollama").start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return;
            }
        } catch (Exception ignored) {
            // Fallback su comando diretto
        }

        try {
            // Prova 2: avvio diretto in background tramite ProcessBuilder usando ~/.local/bin/ollama se presente
            String userHome = System.getProperty("user.home", "");
            java.io.File localWrapper = new java.io.File(userHome, ".local/bin/ollama");
            ProcessBuilder pb;
            if (localWrapper.exists() && localWrapper.canExecute()) {
                pb = new ProcessBuilder(localWrapper.getAbsolutePath(), "serve");
            } else {
                pb = new ProcessBuilder("ollama", "serve");
            }
            pb.environment().put("OLLAMA_VULKAN", "1");
            pb.environment().put("OLLAMA_IGPU_ENABLE", "1");
            pb.redirectErrorStream(true).start();
        } catch (IOException e) {
            log.error("Fallito anche l'avvio del comando 'ollama serve': {}", e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaTagsResponse(List<ModelTag> models) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelTag(String name, String model) {}
}
