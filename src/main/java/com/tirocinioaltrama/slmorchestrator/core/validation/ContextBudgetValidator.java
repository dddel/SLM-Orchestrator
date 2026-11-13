package com.tirocinioaltrama.slmorchestrator.core.validation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.tirocinioaltrama.slmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineConfigurationException;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.SystemPromptResolver;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;

/**
 * Guardia pre-volo e post-hoc per il rispetto della finestra di contesto ({@code num_ctx}).
 * <p>
 * Calcola all'avvio il budget residuo di token disponibile per il payload utente sottraendo
 * a {@code num_ctx} i token riservati alla generazione ({@code num_predict}), l'eventuale margine
 * aggiuntivo ({@code orchestrator.context.reserved-tokens}) e l'overhead statico massimo di ogni
 * pipeline (system prompt + JSON schema + eventuale iniezione del catalogo ruoli/estrazione per EMAIL).
 * <p>
 * Applica una politica Fail-Fast al bootstrap se {@code budgetPayloadTokens <= 0} e valida a
 * runtime la dimensione stimata del payload restituendo {@link ValidationResult} con codice
 * {@link ValidationErrorCode#CONTEXT_TRUNCATED} (mappato su HTTP 422 da {@code DefaultOrchestrator}).
 */
@Component
public class ContextBudgetValidator {

    private static final Logger log = LoggerFactory.getLogger(ContextBudgetValidator.class);

    public static final double DEFAULT_CHARS_PER_TOKEN = 2.5;
    public static final int DEFAULT_RESERVED_TOKENS = 0;
    private static final int CONTEXT_INJECTION_EXTRA_OVERHEAD_TOKENS = 200;
    private static final int RETRY_FEEDBACK_OVERHEAD_TOKENS = 90;

    private final int numCtx;
    private final int numPredict;
    private final double charsPerToken;
    private final int reservedTokens;
    private final Map<DocumentType, Integer> pipelineStaticOverheadTokens;
    private final Map<DocumentType, Integer> pipelinePayloadBudgetTokens;
    private final int routerPayloadBudgetTokens;
    private final int minPayloadBudgetTokens;

    /**
     * Costruttore predefinito con parametri standard (per test unitari leggeri).
     */
    public ContextBudgetValidator() {
        this(
                OllamaProperties.DEFAULT_NUM_CTX,
                OllamaProperties.DEFAULT_NUM_PREDICT,
                DEFAULT_CHARS_PER_TOKEN,
                DEFAULT_RESERVED_TOKENS,
                Map.of(
                        DocumentType.FATTURA, 450,
                        DocumentType.ANAGRAFICA, 450,
                        DocumentType.EMAIL, 650,
                        DocumentType.ALTRO, 350
                ),
                200
        );
    }

    public ContextBudgetValidator(
            int numCtx,
            int numPredict,
            double charsPerToken,
            int reservedTokens,
            Map<DocumentType, Integer> staticOverheads,
            int routerStaticOverheadTokens
    ) {
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.charsPerToken = charsPerToken > 0 ? charsPerToken : DEFAULT_CHARS_PER_TOKEN;
        this.reservedTokens = Math.max(0, reservedTokens);
        this.pipelineStaticOverheadTokens = staticOverheads != null
                ? new EnumMap<>(staticOverheads)
                : new EnumMap<>(DocumentType.class);

        Map<DocumentType, Integer> budgets = new EnumMap<>(DocumentType.class);
        int minBudget = Integer.MAX_VALUE;
        for (Map.Entry<DocumentType, Integer> entry : this.pipelineStaticOverheadTokens.entrySet()) {
            int budget = numCtx - numPredict - this.reservedTokens - entry.getValue();
            budgets.put(entry.getKey(), budget);
            if (budget < minBudget) {
                minBudget = budget;
            }
        }
        this.pipelinePayloadBudgetTokens = Collections.unmodifiableMap(budgets);
        this.routerPayloadBudgetTokens = numCtx - numPredict - this.reservedTokens - Math.max(0, routerStaticOverheadTokens);
        this.minPayloadBudgetTokens = minBudget == Integer.MAX_VALUE
                ? this.routerPayloadBudgetTokens
                : Math.min(minBudget, this.routerPayloadBudgetTokens);
    }

    @Autowired
    public ContextBudgetValidator(
            Environment environment,
            YamlPipelineLoader yamlPipelineLoader,
            ResourceLoader resourceLoader
    ) {
        ResourceLoader loader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
        this.numCtx = environment != null
                ? environment.getProperty("ollama.num-ctx", Integer.class, OllamaProperties.DEFAULT_NUM_CTX)
                : OllamaProperties.DEFAULT_NUM_CTX;
        this.numPredict = environment != null
                ? environment.getProperty("ollama.num-predict", Integer.class, OllamaProperties.DEFAULT_NUM_PREDICT)
                : OllamaProperties.DEFAULT_NUM_PREDICT;
        double configuredCpt = environment != null
                ? environment.getProperty("orchestrator.context.chars-per-token", Double.class, DEFAULT_CHARS_PER_TOKEN)
                : DEFAULT_CHARS_PER_TOKEN;
        this.charsPerToken = configuredCpt > 0 ? configuredCpt : DEFAULT_CHARS_PER_TOKEN;
        int configuredReserved = environment != null
                ? environment.getProperty("orchestrator.context.reserved-tokens", Integer.class, DEFAULT_RESERVED_TOKENS)
                : DEFAULT_RESERVED_TOKENS;
        this.reservedTokens = Math.max(0, configuredReserved);

        String routerPromptSpec = environment != null
                ? environment.getProperty("ollama.router-system-prompt", OllamaProperties.DEFAULT_ROUTER_SYSTEM_PROMPT)
                : OllamaProperties.DEFAULT_ROUTER_SYSTEM_PROMPT;
        int routerOverhead = estimateTokensForResourceOrText(routerPromptSpec, loader) + 40;

        Map<DocumentType, Integer> overheads = new EnumMap<>(DocumentType.class);
        if (yamlPipelineLoader != null) {
            List<PipelineDefinition> defs = yamlPipelineLoader.loadDefinitions();
            for (PipelineDefinition def : defs) {
                if (def.getDocumentType() == null || def.getSteps() == null) {
                    continue;
                }
                DocumentType docType;
                try {
                    docType = DocumentType.valueOf(def.getDocumentType().strip().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                int maxStepOverhead = 0;
                boolean multiStepEnrichment = hasMultiStepOrToolEnrichment(def);
                for (StepDefinition step : def.getSteps()) {
                    if (step.getType() != null && "model_inference".equalsIgnoreCase(step.getType().strip())) {
                        int promptTokens = estimateTokensForPrompt(step.getSystemPrompt(), loader, def.getId(), step.getId());
                        int schemaTokens = estimateTokensForSchema(step.getSchema(), loader);
                        int stepOverhead = promptTokens + schemaTokens + RETRY_FEEDBACK_OVERHEAD_TOKENS;
                        if (requiresContextInjectionOverhead(step, multiStepEnrichment)) {
                            stepOverhead += CONTEXT_INJECTION_EXTRA_OVERHEAD_TOKENS;
                        }
                        if (stepOverhead > maxStepOverhead) {
                            maxStepOverhead = stepOverhead;
                        }
                    }
                }
                overheads.put(docType, maxStepOverhead);
            }
        }
        this.pipelineStaticOverheadTokens = Collections.unmodifiableMap(overheads);

        Map<DocumentType, Integer> budgets = new EnumMap<>(DocumentType.class);
        int minBudget = Integer.MAX_VALUE;
        for (Map.Entry<DocumentType, Integer> entry : this.pipelineStaticOverheadTokens.entrySet()) {
            int budget = this.numCtx - this.numPredict - this.reservedTokens - entry.getValue();
            budgets.put(entry.getKey(), budget);
            if (budget < minBudget) {
                minBudget = budget;
            }
        }
        this.pipelinePayloadBudgetTokens = Collections.unmodifiableMap(budgets);
        this.routerPayloadBudgetTokens = this.numCtx - this.numPredict - this.reservedTokens - routerOverhead;
        this.minPayloadBudgetTokens = minBudget == Integer.MAX_VALUE
                ? this.routerPayloadBudgetTokens
                : Math.min(minBudget, this.routerPayloadBudgetTokens);
    }

    @PostConstruct
    public void validateConfigurationAtStartup() {
        if (numCtx <= 0) {
            throw new PipelineConfigurationException("Valore 'ollama.num-ctx' non valido (" + numCtx + "): deve essere > 0.");
        }
        if (numPredict <= 0 || numPredict >= numCtx) {
            throw new PipelineConfigurationException("Configurazione finestra di contesto incoerente: ollama.num-predict ("
                    + numPredict + ") deve essere positivo e strettamente inferiore a ollama.num-ctx (" + numCtx + ").");
        }
        if (routerPayloadBudgetTokens <= 0) {
            throw new PipelineConfigurationException("Budget di contesto insufficiente per il Router: num_ctx=" + numCtx
                    + ", num_predict=" + numPredict + ", reserved=" + reservedTokens
                    + " -> budgetPayloadTokens=" + routerPayloadBudgetTokens + " (deve essere > 0).");
        }
        for (Map.Entry<DocumentType, Integer> entry : pipelinePayloadBudgetTokens.entrySet()) {
            if (entry.getValue() <= 0) {
                throw new PipelineConfigurationException("Budget di contesto insufficiente per la pipeline '"
                        + entry.getKey() + "': num_ctx=" + numCtx
                        + ", num_predict=" + numPredict
                        + ", overheadStatico=" + pipelineStaticOverheadTokens.get(entry.getKey())
                        + ", reserved=" + reservedTokens
                        + " -> budgetPayloadTokens=" + entry.getValue() + " (deve essere > 0).");
            }
            log.info("ContextBudgetValidator [{}]: num_ctx={}, num_predict={}, overheadStatico≈{} token -> budgetPayload={} token (~{} caratteri)",
                    entry.getKey(), numCtx, numPredict,
                    pipelineStaticOverheadTokens.get(entry.getKey()),
                    entry.getValue(),
                    (int) Math.floor(entry.getValue() * charsPerToken));
        }
    }

    /**
     * Valida preventivamente che il payload utente rispetti il budget di token della pipeline
     * (o il budget minimo tra router e pipeline se il tipo documento non è ancora noto).
     */
    public ValidationResult validatePayloadBudget(String payload, DocumentType type) {
        if (payload == null || payload.isBlank()) {
            return ValidationResult.valid();
        }
        int estimatedPayloadTokens = estimateTokens(payload);
        int allowedTokens = getBudgetTokensFor(type);

        if (estimatedPayloadTokens > allowedTokens) {
            int maxCharsApprox = (int) Math.floor(allowedTokens * charsPerToken);
            String targetDesc = type != null ? "la pipeline " + type : "la finestra di contesto del modello";
            String msg = String.format(
                    Locale.ROOT,
                    "Il payload supera il budget della finestra di contesto per %s: stimati %d token (%d caratteri), "
                            + "massimo consentito %d token (~%d caratteri con num_ctx=%d, num_predict=%d).",
                    targetDesc,
                    estimatedPayloadTokens,
                    payload.length(),
                    allowedTokens,
                    maxCharsApprox,
                    numCtx,
                    numPredict
            );
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("$", "payload", ValidationErrorCode.CONTEXT_TRUNCATED, msg)),
                    List.of()
            );
        }
        return ValidationResult.valid();
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / charsPerToken);
    }

    public int getBudgetTokensFor(DocumentType type) {
        if (type != null && pipelinePayloadBudgetTokens.containsKey(type)) {
            return pipelinePayloadBudgetTokens.get(type);
        }
        return minPayloadBudgetTokens;
    }

    public int getNumCtx() {
        return numCtx;
    }

    public int getNumPredict() {
        return numPredict;
    }

    private static boolean hasMultiStepOrToolEnrichment(PipelineDefinition def) {
        if (def == null || def.getSteps() == null) {
            return false;
        }
        int inferenceSteps = 0;
        for (StepDefinition step : def.getSteps()) {
            if (step == null || step.getType() == null) {
                continue;
            }
            String t = step.getType().strip().toLowerCase(Locale.ROOT);
            if ("tool".equals(t) || "decision".equals(t)) {
                return true;
            }
            if ("model_inference".equals(t)) {
                inferenceSteps++;
            }
        }
        return inferenceSteps > 1;
    }

    private static boolean requiresContextInjectionOverhead(StepDefinition step, boolean multiStepEnrichment) {
        if (step == null) {
            return false;
        }
        if (step.getInjectContext() != null && !step.getInjectContext().isEmpty()) {
            return true;
        }
        if (step.getMergeFields() != null && !step.getMergeFields().isEmpty()) {
            return true;
        }
        return multiStepEnrichment;
    }

    private int estimateTokensForPrompt(String promptSpec, ResourceLoader loader, String pipelineId, String stepId) {
        if (promptSpec == null || promptSpec.isBlank()) {
            return 0;
        }
        try {
            String resolved = SystemPromptResolver.resolve(promptSpec, loader,
                    "lo step '" + stepId + "' della pipeline '" + pipelineId + "'");
            return estimateTokens(resolved);
        } catch (Exception e) {
            return estimateTokens(promptSpec);
        }
    }

    private int estimateTokensForResourceOrText(String spec, ResourceLoader loader) {
        if (spec == null || spec.isBlank()) {
            return 0;
        }
        try {
            String resolved = SystemPromptResolver.resolve(spec, loader, "router");
            return estimateTokens(resolved);
        } catch (Exception e) {
            return estimateTokens(spec);
        }
    }

    private int estimateTokensForSchema(String schemaPath, ResourceLoader loader) {
        if (schemaPath == null || schemaPath.isBlank()) {
            return 0;
        }
        String resolvedPath = schemaPath.strip();
        if (!resolvedPath.startsWith("classpath:") && !resolvedPath.startsWith("file:")) {
            resolvedPath = "classpath:" + (resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath);
        }
        try {
            Resource res = loader.getResource(resolvedPath);
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    // La grammatica GBNF compilata da Ollama incide sul contesto in misura ridotta (~35% del JSON schema grezzo)
                    return Math.max(20, (int) Math.ceil((content.length() * 0.35) / charsPerToken));
                }
            }
        } catch (Exception ignored) {
        }
        return 60;
    }
}
