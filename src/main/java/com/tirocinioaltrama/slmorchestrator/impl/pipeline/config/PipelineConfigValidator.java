package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineConfigurationException;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldPath;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition.FieldValidatorDefinition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validatore stringente per il bootstrap delle pipeline dichiarative definite in YAML.
 * Applica la policy Fail-Fast all'avvio dell'applicazione:
 * 1. Schema JSON referenziato esistente e sintatticamente valido
 * 2. Validatore con nome registrato in ValidatorRegistry
 * 3. Campo da validare esistente nello schema JSON
 * 4. DocumentType valido e non duplicato
 * 5. type: tool esplicitamente rifiutato finché ToolRegistry non esiste (RF8)
 * 6. type sconosciuto rifiutato con messaggio di errore dedicato
 * 7. Campi dello schema con nomi corrispondenti a validatori noti (token matching)
 *    devono essere coperti da validators o esplicitamente dichiarati in 'unvalidated'
 * 8. Una field_validation richiede esattamente un model_inference che la precede
 * 9. Il 'system_prompt' di uno step model_inference, se dichiarato, deve risolversi in una risorsa
 *    esistente e non vuota (o in un testo inline non vuoto)
 */
@Component
public class PipelineConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfigValidator.class);

    // Token sequenze dei validatori noti
    private static final List<List<String>> KNOWN_VALIDATOR_TOKENS = List.of(
            List.of("partita", "iva"),
            List.of("codice", "fiscale"),
            List.of("iban"),
            List.of("email")
    );

    private final ValidatorRegistry validatorRegistry;
    private final ToolRegistry toolRegistry;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public PipelineConfigValidator(
            ValidatorRegistry validatorRegistry,
            ToolRegistry toolRegistry,
            ResourceLoader resourceLoader
    ) {
        this.validatorRegistry = validatorRegistry;
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        this.resourceLoader = resourceLoader;
        this.objectMapper = new ObjectMapper();
    }

    public void validateAll(List<PipelineDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new PipelineConfigurationException("Nessuna pipeline configurata trovata nei file YAML.");
        }

        Set<DocumentType> seenDocTypes = new HashSet<>();

        for (PipelineDefinition def : definitions) {
            validatePipeline(def, seenDocTypes);
        }
    }

    private void validatePipeline(PipelineDefinition def, Set<DocumentType> seenDocTypes) {
        if (def.getDocumentType() == null || def.getDocumentType().isBlank()) {
            throw new PipelineConfigurationException("Pipeline con id '" + def.getId() + "' non dichiara alcun 'documentType'.");
        }

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(def.getDocumentType().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PipelineConfigurationException("Valore 'documentType' non valido ('" + def.getDocumentType()
                    + "') per la pipeline '" + def.getId() + "'.");
        }

        validateActionsIfPresent(def);

        if (!seenDocTypes.add(docType)) {
            throw new PipelineConfigurationException("DocumentType duplicato rilevato nella configurazione YAML: " + docType);
        }

        if (def.getSteps() == null || def.getSteps().isEmpty()) {
            throw new PipelineConfigurationException("La pipeline '" + def.getId() + "' (" + docType + ") non contiene alcuno step.");
        }

        Map<String, StepDefinition> priorSteps = new java.util.LinkedHashMap<>();
        JsonNode activeSchemaRoot = null;
        List<JsonNode> allSchemaRoots = new ArrayList<>();
        int inferenceStepCount = 0;

        // Raccogliamo a livello di pipeline tutti i percorsi validati e unvalidated
        Set<String> allValidatedPaths = new HashSet<>();
        Set<String> allUnvalidatedPaths = new HashSet<>();

        for (StepDefinition step : def.getSteps()) {
            validateStep(step, def.getId(), priorSteps);
            priorSteps.put(step.getId().strip(), step);

            String type = step.getType().strip().toLowerCase(Locale.ROOT);
            if ("model_inference".equals(type)) {
                activeSchemaRoot = loadAndValidateSchema(step.getSchema(), def.getId(), step.getId());
                allSchemaRoots.add(activeSchemaRoot);
                inferenceStepCount++;
            } else if ("field_validation".equals(type)) {
                if (inferenceStepCount < 1) {
                    throw new PipelineConfigurationException("Lo step 'field_validation' con id '" + step.getId()
                            + "' nella pipeline '" + def.getId() + "' richiede almeno un antecedente step 'model_inference' (trovati: "
                            + inferenceStepCount + ").");
                }
                validateFieldValidationStep(step, def.getId(), activeSchemaRoot, allValidatedPaths, allUnvalidatedPaths);
            }
        }

        // Verifica unvalidated vs validated e copertura campi schema
        for (JsonNode schemaRoot : allSchemaRoots) {
            verifySchemaFieldsCoverage(def.getId(), schemaRoot, allValidatedPaths, allUnvalidatedPaths);
        }
    }

    private void validateStep(StepDefinition step, String pipelineId, Map<String, StepDefinition> priorSteps) {
        if (step.getId() == null || step.getId().isBlank()) {
            throw new PipelineConfigurationException("Step senza 'id' nella pipeline '" + pipelineId + "'.");
        }
        if (priorSteps.containsKey(step.getId().strip())) {
            throw new PipelineConfigurationException("Step id duplicato '" + step.getId() + "' nella pipeline '" + pipelineId + "'.");
        }

        if (step.getType() == null || step.getType().isBlank()) {
            throw new PipelineConfigurationException("Step '" + step.getId() + "' nella pipeline '" + pipelineId + "' non ha un 'type' definito.");
        }

        String type = step.getType().strip().toLowerCase(Locale.ROOT);
        switch (type) {
            case "model_inference" -> {
                if (step.getOnFailure() != null && !step.getOnFailure().isEmpty()) {
                    throw new PipelineConfigurationException("La policy 'on_failure' è ammessa solo su step di tipo 'field_validation' (trovata nello step '" + step.getId() + "').");
                }
                if (step.getModel() == null || step.getModel().isBlank()) {
                    throw new PipelineConfigurationException("Step 'model_inference' con id '" + step.getId()
                            + "' nella pipeline '" + pipelineId + "' richiede il campo 'model'.");
                }
                if (step.getSchema() == null || step.getSchema().isBlank()) {
                    throw new PipelineConfigurationException("Step 'model_inference' con id '" + step.getId()
                            + "' nella pipeline '" + pipelineId + "' richiede il campo 'schema'.");
                }
                if (step.getUnvalidated() != null && !step.getUnvalidated().isEmpty()) {
                    throw new PipelineConfigurationException("La direttiva 'unvalidated' è ammessa solo su step di tipo 'field_validation' (trovata nello step '" + step.getId() + "').");
                }
                if (step.getSystemPrompt() != null) {
                    // Fail-Fast: la risorsa (o il testo inline) deve esistere ed essere non vuota
                    SystemPromptResolver.resolve(step.getSystemPrompt(), resourceLoader,
                            "lo step '" + step.getId() + "' della pipeline '" + pipelineId + "'");
                } else {
                    log.warn("Lo step 'model_inference' '{}' della pipeline '{}' non dichiara 'system_prompt': "
                            + "il modello base verrà invocato senza istruzioni di sistema specifiche.", step.getId(), pipelineId);
                }
            }
            case "field_validation" -> {
                validateOnFailureIfPresent(step, pipelineId, priorSteps);
            }
            case "tool" -> {
                if (step.getOnFailure() != null && !step.getOnFailure().isEmpty()) {
                    throw new PipelineConfigurationException("La policy 'on_failure' è ammessa solo su step di tipo 'field_validation' (trovata nello step '" + step.getId() + "').");
                }
                String toolName = step.getTool() != null ? step.getTool().strip() : null;
                if (toolName == null || toolName.isBlank()) {
                    throw new PipelineConfigurationException("Step 'tool' con id '" + step.getId()
                            + "' nella pipeline '" + pipelineId + "' non specifica il nome del 'tool'.");
                }
                if (!toolRegistry.contains(toolName.toLowerCase(Locale.ROOT))) {
                    throw new PipelineConfigurationException("Step 'tool' con id '" + step.getId()
                            + "' nella pipeline '" + pipelineId + "': il tool '" + toolName
                            + "' non è registrato in ToolRegistry o non è ancora supportato (disponibili: " + toolRegistry.getRegisteredNames() + ").");
                }
                validateToolExecutionPolicyParams(
                        step.getTimeoutMs(),
                        step.getMaxRetries(),
                        step.getRetryBackoffMs(),
                        "lo step 'tool' '" + step.getId() + "' della pipeline '" + pipelineId + "'"
                );
            }
            case "decision" -> {
                if (step.getOnFailure() != null && !step.getOnFailure().isEmpty()) {
                    throw new PipelineConfigurationException("La policy 'on_failure' è ammessa solo su step di tipo 'field_validation' (trovata nello step '" + step.getId() + "').");
                }
                if (step.getSystemPrompt() != null) {
                    SystemPromptResolver.resolve(step.getSystemPrompt(), resourceLoader,
                            "lo step '" + step.getId() + "' della pipeline '" + pipelineId + "'");
                }
            }
            default -> throw new PipelineConfigurationException("Tipo di step sconosciuto: '" + step.getType()
                    + "' nello step '" + step.getId() + "' della pipeline '" + pipelineId + "'.");
        }
    }

    private void validateOnFailureIfPresent(StepDefinition step, String pipelineId, Map<String, StepDefinition> priorSteps) {
        if (step.getOnFailure() == null || step.getOnFailure().isEmpty()) {
            return;
        }

        OnFailureDefinition of = step.getOnFailureDefinition();
        if (of == null) {
            of = OnFailureDefinition.fromMap(step.getOnFailure());
            step.setOnFailureDefinition(of);
        }

        if (of.getRetryStep() == null || of.getRetryStep().isBlank()) {
            throw new PipelineConfigurationException("La policy 'on_failure' dello step '" + step.getId()
                    + "' nella pipeline '" + pipelineId + "' deve specificare 'retry_step'.");
        }

        String targetStepId = of.getRetryStep().strip();
        if (targetStepId.equals(step.getId().strip())) {
            throw new PipelineConfigurationException("Lo step '" + step.getId()
                    + "' nella pipeline '" + pipelineId + "' non può dichiarare se stesso come 'retry_step'.");
        }

        // Il retry_step deve precedere lo step corrente ed essere di tipo model_inference
        StepDefinition targetStep = priorSteps.get(targetStepId);
        if (targetStep == null) {
            throw new PipelineConfigurationException("Il 'retry_step' '" + targetStepId
                    + "' dichiarato nello step '" + step.getId() + "' della pipeline '" + pipelineId
                    + "' non esiste oppure non precede lo step corrente.");
        }
        if (!"model_inference".equalsIgnoreCase(targetStep.getType())) {
            throw new PipelineConfigurationException("Il 'retry_step' '" + targetStepId
                    + "' dichiarato nello step '" + step.getId() + "' della pipeline '" + pipelineId
                    + "' deve essere di tipo 'model_inference' (trovato: '" + targetStep.getType() + "').");
        }

        if (of.getMaxRetries() <= 0) {
            throw new PipelineConfigurationException("Il parametro 'max_retries' nello step '" + step.getId()
                    + "' della pipeline '" + pipelineId + "' deve essere maggiore o uguale a 1 (trovato: " + of.getMaxRetries() + ").");
        }

        if (of.getEscalateTo() != null) {
            if (of.getEscalateTo().isBlank()) {
                throw new PipelineConfigurationException("La configurazione 'escalate_to' nello step '" + step.getId()
                        + "' della pipeline '" + pipelineId + "' non può essere vuota.");
            }
        }
    }

    private void validateActionsIfPresent(PipelineDefinition def) {
        if (def.getActions() == null) {
            return;
        }

        if (def.getActions().getOnOk() != null) {
            validateActionList(def.getActions().getOnOk(), "on_ok", def.getId());
        }
        if (def.getActions().getOnIndeterminate() != null) {
            validateActionList(def.getActions().getOnIndeterminate(), "on_indeterminate", def.getId());
        }
    }

    private void validateActionList(List<ActionDefinition> actions, String groupName, String pipelineId) {
        for (ActionDefinition action : actions) {
            if (action.getTool() == null || action.getTool().isBlank()) {
                throw new PipelineConfigurationException("Azione nel blocco '" + groupName
                        + "' della pipeline '" + pipelineId + "' non specifica il nome del 'tool'.");
            }
            String toolName = action.getTool().strip().toLowerCase(Locale.ROOT);
            if (!toolRegistry.contains(toolName)) {
                throw new PipelineConfigurationException("Tool '" + action.getTool()
                        + "' referenziato nel blocco '" + groupName + "' della pipeline '" + pipelineId
                        + "' non è registrato in ToolRegistry (disponibili: " + toolRegistry.getRegisteredNames() + ").");
            }

            if (action.getArgs() != null) {
                for (Map.Entry<String, Object> entry : action.getArgs().entrySet()) {
                    if (entry.getValue() instanceof String strVal && strVal.startsWith("$")) {
                        if (!"$result".equals(strVal) && !"$unvalidatedResult".equals(strVal) && !"$errors".equals(strVal)) {
                            throw new PipelineConfigurationException("Placeholder non supportato '" + strVal
                                    + "' nell'argomento '" + entry.getKey() + "' del tool '" + action.getTool()
                                    + "' (supportati: $result, $unvalidatedResult, $errors).");
                        }
                    }
                }
            }
            validateToolExecutionPolicyParams(
                    action.getTimeoutMs(),
                    action.getMaxRetries(),
                    action.getRetryBackoffMs(),
                    "l'azione '" + action.getTool() + "' nel blocco '" + groupName + "' della pipeline '" + pipelineId + "'"
            );
        }
    }

    private void validateToolExecutionPolicyParams(
            Long timeoutMs,
            Integer maxRetries,
            Long retryBackoffMs,
            String contextLabel
    ) {
        if (timeoutMs != null && (timeoutMs <= 0 || timeoutMs > 120000L)) {
            throw new PipelineConfigurationException("Valore 'timeout_ms' non valido (" + timeoutMs
                    + ") per " + contextLabel + ": deve essere compreso tra 1 e 120000 ms.");
        }
        if (maxRetries != null && (maxRetries < 0 || maxRetries > 5)) {
            throw new PipelineConfigurationException("Valore 'max_retries' non valido (" + maxRetries
                    + ") per " + contextLabel + ": deve essere compreso tra 0 e 5.");
        }
        if (retryBackoffMs != null && retryBackoffMs < 0) {
            throw new PipelineConfigurationException("Valore 'retry_backoff_ms' non valido (" + retryBackoffMs
                    + ") per " + contextLabel + ": deve essere maggiore o uguale a 0.");
        }
    }

    private JsonNode loadAndValidateSchema(String schemaPath, String pipelineId, String stepId) {
        String resolvedPath = schemaPath.strip();
        if (!resolvedPath.startsWith("classpath:") && !resolvedPath.startsWith("file:")) {
            resolvedPath = "classpath:" + (resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath);
        }

        Resource resource = resourceLoader.getResource(resolvedPath);
        if (!resource.exists()) {
            throw new PipelineConfigurationException("Schema JSON non trovato al percorso '" + schemaPath
                    + "' per lo step '" + stepId + "' della pipeline '" + pipelineId + "'.");
        }

        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new PipelineConfigurationException("Impossibile leggere o analizzare lo schema JSON al percorso '"
                    + schemaPath + "': " + e.getMessage(), e);
        }
    }

    private void validateFieldValidationStep(
            StepDefinition step,
            String pipelineId,
            JsonNode schemaRoot,
            Set<String> allValidatedPaths,
            Set<String> allUnvalidatedPaths
    ) {
        List<FieldValidatorDefinition> validators = step.getValidators();
        if (validators != null) {
            for (FieldValidatorDefinition fvd : validators) {
                if (fvd.getValidator() == null || fvd.getValidator().isBlank()) {
                    throw new PipelineConfigurationException("Nome validatore mancante nella definizione di validazione dello step '"
                            + step.getId() + "' della pipeline '" + pipelineId + "'.");
                }
                String validatorName = fvd.getValidator().strip();
                if (!validatorRegistry.contains(validatorName)) {
                    throw new PipelineConfigurationException("Validatore sconosciuto '" + validatorName + "' nello step '"
                            + step.getId() + "' della pipeline '" + pipelineId
                            + "'. Validatori disponibili: " + validatorRegistry.getAvailableValidatorNames());
                }

                if (fvd.getField() == null || fvd.getField().isBlank()) {
                    throw new PipelineConfigurationException("Campo non specificato per il validatore '" + validatorName
                            + "' nello step '" + step.getId() + "' della pipeline '" + pipelineId + "'.");
                }

                FieldPath path = new FieldPath(fvd.getField().strip());
                if (schemaRoot != null && !path.existsInJsonSchema(schemaRoot)) {
                    throw new PipelineConfigurationException("Il campo '" + path.getOriginalPath()
                            + "' dichiarato per il validatore '" + validatorName
                            + "' nello step '" + step.getId() + "' non esiste nello schema JSON della pipeline '" + pipelineId + "'.");
                }
                allValidatedPaths.add(path.getOriginalPath());
            }
        }

        List<String> unvalidatedList = step.getUnvalidated();
        if (unvalidatedList != null) {
            for (String unvField : unvalidatedList) {
                if (unvField == null || unvField.isBlank()) {
                    continue;
                }
                FieldPath path = new FieldPath(unvField.strip());
                if (schemaRoot != null && !path.existsInJsonSchema(schemaRoot)) {
                    throw new PipelineConfigurationException("Il campo '" + path.getOriginalPath()
                            + "' dichiarato in 'unvalidated' nello step '" + step.getId()
                            + "' non esiste nello schema JSON della pipeline '" + pipelineId + "'.");
                }
                allUnvalidatedPaths.add(path.getOriginalPath());
            }
        }
    }

    private void verifySchemaFieldsCoverage(
            String pipelineId,
            JsonNode schemaRoot,
            Set<String> allValidatedPaths,
            Set<String> allUnvalidatedPaths
    ) {
        // Controllo overlap tra validated e unvalidated
        for (String path : allValidatedPaths) {
            if (allUnvalidatedPaths.contains(path)) {
                throw new PipelineConfigurationException("Il campo '" + path + "' nella pipeline '" + pipelineId
                        + "' non può essere contemporaneamente validato ed esentato in 'unvalidated'.");
            }
        }

        // Ispezione di tutte le proprietà dello schema per trovare campi che matchano validatori noti
        List<String> schemaMatchingPaths = new ArrayList<>();
        findMatchingSchemaFields(schemaRoot, "", schemaMatchingPaths);

        for (String matchingPath : schemaMatchingPaths) {
            if (!allValidatedPaths.contains(matchingPath) && !allUnvalidatedPaths.contains(matchingPath)) {
                throw new PipelineConfigurationException("Il campo '" + matchingPath + "' nello schema JSON della pipeline '"
                        + pipelineId + "' corrisponde a un validatore noto ma non è coperto in 'validators' né esentato in 'unvalidated'.");
            }
        }
    }

    private void findMatchingSchemaFields(JsonNode currentNode, String currentPath, List<String> result) {
        if (currentNode == null || !currentNode.isObject()) {
            return;
        }

        if (currentNode.has("properties")) {
            JsonNode properties = currentNode.get("properties");
            properties.forEachEntry((propName, propNode) -> {
                String fullPath = currentPath.isEmpty() ? propName : currentPath + "." + propName;
                if (matchesKnownValidator(propName)) {
                    result.add(fullPath);
                }
                findMatchingSchemaFields(propNode, fullPath, result);
            });
        } else if (currentNode.has("items") && currentNode.get("items").isObject()) {
            findMatchingSchemaFields(currentNode.get("items"), currentPath, result);
        }
    }

    /**
     * Confronto per token: spezza il nome della proprietà su camelCase e underscore
     * e controlla se contiene come sottosequenza contigua i token di uno dei validatori noti.
     */
    public static boolean matchesKnownValidator(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            return false;
        }

        List<String> propTokens = tokenize(propertyName);
        if (propTokens.isEmpty()) {
            return false;
        }

        for (List<String> validatorTokens : KNOWN_VALIDATOR_TOKENS) {
            if (containsSubsequence(propTokens, validatorTokens)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> tokenize(String input) {
        // Separa prima su underscore e caratteri non alfabetici
        String[] parts = input.split("[_\\-]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) continue;
            // Separa su transizioni camelCase (es. partitaIva -> partita, Iva; emailMittente -> email, Mittente)
            String[] camelParts = part.split("(?<=[a-z0-9])(?=[A-Z])");
            for (String cp : camelParts) {
                if (!cp.isBlank()) {
                    tokens.add(cp.toLowerCase(Locale.ROOT));
                }
            }
        }
        return tokens;
    }

    private static boolean containsSubsequence(List<String> source, List<String> target) {
        if (target.isEmpty() || source.size() < target.size()) {
            return false;
        }
        for (int i = 0; i <= source.size() - target.size(); i++) {
            boolean match = true;
            for (int j = 0; j < target.size(); j++) {
                if (!source.get(i + j).equals(target.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
