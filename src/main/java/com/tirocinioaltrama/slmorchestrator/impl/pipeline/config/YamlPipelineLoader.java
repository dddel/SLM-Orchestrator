package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineConfigurationException;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldPath;
import com.tirocinioaltrama.slmorchestrator.core.validation.RequiredFieldSpec;
import com.tirocinioaltrama.slmorchestrator.core.validation.Validator;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.BasePipeline;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.DecisionPipelineStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.ModelInferenceStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.ToolPipelineStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep.PathValidatorBinding;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition.FieldValidatorDefinition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Caricatore e costruttore di pipeline a partire da definizioni YAML.
 * Se 'orchestrator.pipelines.dir' è configurata, carica i file da tale directory
 * (lanciando PipelineConfigurationException se inesistente); se vuota o assente,
 * carica le risorse predefinite da classpath:pipelines/*.yaml.
 */
@Component
public class YamlPipelineLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlPipelineLoader.class);

    private final Environment environment;
    private final ValidatorRegistry validatorRegistry;
    private final PipelineConfigValidator configValidator;
    private final ToolRegistry toolRegistry;
    private final PathMatchingResourcePatternResolver resourceResolver;
    private final ObjectMapper objectMapper;

    public YamlPipelineLoader(
            Environment environment,
            ValidatorRegistry validatorRegistry,
            PipelineConfigValidator configValidator
    ) {
        this(environment, validatorRegistry, configValidator, new ToolRegistry());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public YamlPipelineLoader(
            Environment environment,
            ValidatorRegistry validatorRegistry,
            PipelineConfigValidator configValidator,
            ToolRegistry toolRegistry
    ) {
        this.environment = environment;
        this.validatorRegistry = validatorRegistry;
        this.configValidator = configValidator;
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        this.resourceResolver = new PathMatchingResourcePatternResolver();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Carica tutte le definizioni YAML, ne valida la correttezza stringente e istanzia le Pipeline.
     */
    public List<Pipeline> loadPipelines(ModelClient modelClient) {
        List<PipelineDefinition> definitions = loadDefinitions();
        // Validazione Fail-Fast stringente all'avvio
        configValidator.validateAll(definitions);

        List<Pipeline> pipelines = new ArrayList<>();
        for (PipelineDefinition def : definitions) {
            pipelines.add(buildPipeline(def, modelClient));
        }
        return pipelines;
    }

    /**
     * Legge e parsa le definizioni YAML risolvendo i placeholder di configurazione tramite Spring Environment.
     */
    public List<PipelineDefinition> loadDefinitions() {
        String customDir = environment.getProperty("orchestrator.pipelines.dir");
        if (customDir != null && !customDir.isBlank()) {
            File dir = new File(customDir.strip());
            if (dir.exists() && dir.isDirectory()) {
                log.info("Caricamento pipeline YAML dalla directory esterna: {}", dir.getAbsolutePath());
                return loadFromDirectory(dir);
            } else {
                throw new PipelineConfigurationException("La directory esterna configurata in 'orchestrator.pipelines.dir' non esiste o non è una cartella: " + customDir);
            }
        }

        log.info("Caricamento pipeline YAML dal percorso predefinito in classpath (classpath:pipelines/*.yaml)...");
        return loadFromClasspath();
    }

    private List<PipelineDefinition> loadFromDirectory(File dir) {
        File[] yamlFiles = dir.listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (yamlFiles == null || yamlFiles.length == 0) {
            throw new PipelineConfigurationException("Nessun file YAML trovato nella cartella: " + dir.getAbsolutePath());
        }

        List<PipelineDefinition> list = new ArrayList<>();
        for (File file : yamlFiles) {
            try (InputStream is = new FileInputStream(file)) {
                String rawContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                list.add(parseAndResolveYaml(rawContent, file.getName()));
            } catch (IOException e) {
                throw new PipelineConfigurationException("Errore durante la lettura del file pipeline '" + file.getAbsolutePath() + "': " + e.getMessage(), e);
            }
        }
        return list;
    }

    private List<PipelineDefinition> loadFromClasspath() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath*:pipelines/*.yaml");
            if (resources.length == 0) {
                resources = resourceResolver.getResources("classpath*:pipelines/*.yml");
            }
            if (resources.length == 0) {
                throw new PipelineConfigurationException("Nessun file di pipeline trovato in classpath:pipelines/*.yaml");
            }

            List<PipelineDefinition> list = new ArrayList<>();
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String rawContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    list.add(parseAndResolveYaml(rawContent, resource.getFilename()));
                }
            }
            return list;
        } catch (IOException e) {
            throw new PipelineConfigurationException("Errore durante la scansione delle pipeline in classpath: " + e.getMessage(), e);
        }
    }

    private PipelineDefinition parseAndResolveYaml(String rawContent, String sourceName) {
        String resolvedContent;
        try {
            resolvedContent = environment.resolveRequiredPlaceholders(rawContent);
        } catch (IllegalArgumentException e) {
            throw new PipelineConfigurationException("Impossibile risolvere uno o più placeholder nella pipeline '" + sourceName + "': " + e.getMessage(), e);
        }

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> map = yaml.load(resolvedContent);
        if (map == null) {
            throw new PipelineConfigurationException("Il file YAML '" + sourceName + "' è vuoto o non valido.");
        }

        return mapToDefinition(map, sourceName);
    }

    @SuppressWarnings("unchecked")
    private PipelineDefinition mapToDefinition(Map<String, Object> map, String sourceName) {
        PipelineDefinition def = new PipelineDefinition();
        def.setId((String) map.get("id"));
        def.setDocumentType((String) map.get("documentType"));

        Object stepsObj = map.get("steps");
        if (stepsObj instanceof List<?> stepList) {
            List<StepDefinition> steps = new ArrayList<>();
            for (Object obj : stepList) {
                if (obj instanceof Map<?, ?> stepMap) {
                    Map<String, Object> sm = (Map<String, Object>) stepMap;
                    StepDefinition step = new StepDefinition();
                    step.setId((String) sm.get("id"));
                    step.setType((String) sm.get("type"));
                    step.setModel((String) sm.get("model"));
                    step.setSchema((String) sm.get("schema"));
                    if (sm.get("system_prompt") != null) {
                        step.setSystemPrompt(sm.get("system_prompt").toString());
                    }
                    step.setName((String) sm.get("name"));
                    if (sm.get("tool") != null) {
                        step.setTool(sm.get("tool").toString());
                    }
                    if (sm.get("candidates_source") != null) {
                        step.setCandidatesSource(sm.get("candidates_source").toString());
                    } else if (sm.get("candidatesSource") != null) {
                        step.setCandidatesSource(sm.get("candidatesSource").toString());
                    }
                    if (sm.get("target_field") != null) {
                        step.setTargetField(sm.get("target_field").toString());
                    } else if (sm.get("targetField") != null) {
                        step.setTargetField(sm.get("targetField").toString());
                    }
                    if (sm.get("shuffle_candidates") != null) {
                        step.setShuffleCandidates(Boolean.parseBoolean(sm.get("shuffle_candidates").toString()));
                    } else if (sm.get("shuffleCandidates") != null) {
                        step.setShuffleCandidates(Boolean.parseBoolean(sm.get("shuffleCandidates").toString()));
                    }
                    if (sm.get("args") instanceof Map<?, ?> am) {
                        step.setArgs((Map<String, Object>) am);
                    }
                    if (sm.get("on_failure") instanceof Map<?, ?> ofMap) {
                        step.setOnFailure((Map<String, Object>) ofMap);
                        step.setOnFailureDefinition(OnFailureDefinition.fromMap((Map<String, Object>) ofMap));
                    }
                    if (sm.get("validators") instanceof List<?> valList) {
                        List<FieldValidatorDefinition> validators = new ArrayList<>();
                        for (Object vObj : valList) {
                            if (vObj instanceof Map<?, ?> vm) {
                                String field = (String) vm.get("field");
                                String validator = (String) vm.get("validator");
                                validators.add(new FieldValidatorDefinition(field, validator));
                            }
                        }
                        step.setValidators(validators);
                    }
                    if (sm.get("unvalidated") instanceof List<?> unvList) {
                        List<String> unvalidated = new ArrayList<>();
                        for (Object uObj : unvList) {
                            if (uObj != null) {
                                unvalidated.add(uObj.toString().strip());
                            }
                        }
                        step.setUnvalidated(unvalidated);
                    }
                    steps.add(step);
                }
            }
            def.setSteps(steps);
        }

        Object actionsObj = map.get("actions");
        if (actionsObj instanceof Map<?, ?> actionsMap) {
            ActionsGroupDefinition group = new ActionsGroupDefinition();
            if (actionsMap.get("on_ok") instanceof List<?> onOkList) {
                group.setOnOk(parseActionList((List<?>) onOkList));
            }
            if (actionsMap.get("on_indeterminate") instanceof List<?> onIndetList) {
                group.setOnIndeterminate(parseActionList((List<?>) onIndetList));
            }
            def.setActions(group);
        }

        return def;
    }

    @SuppressWarnings("unchecked")
    private List<ActionDefinition> parseActionList(List<?> list) {
        List<ActionDefinition> actions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> itemMap) {
                Map<String, Object> im = (Map<String, Object>) itemMap;
                String tool = im.get("tool") != null ? im.get("tool").toString().strip() : null;
                Map<String, Object> args = im.get("args") instanceof Map<?, ?> am ? (Map<String, Object>) am : Map.of();
                actions.add(new ActionDefinition(tool, args));
            }
        }
        return actions;
    }

    private Pipeline buildPipeline(PipelineDefinition def, ModelClient modelClient) {
        DocumentType docType = DocumentType.valueOf(def.getDocumentType().strip().toUpperCase());
        List<PipelineStep> steps = new ArrayList<>();

        // Trova lo schema dell'inferenza precedente per associare i RequiredFieldSpec a field_validation
        JsonNode lastInferenceSchema = null;

        for (StepDefinition stepDef : def.getSteps()) {
            String type = stepDef.getType().strip().toLowerCase();
            switch (type) {
                case "model_inference" -> {
                    String schemaContent = loadSchemaContent(stepDef.getSchema());
                    lastInferenceSchema = parseSchemaJson(schemaContent, stepDef.getSchema());
                    String systemPrompt = stepDef.getSystemPrompt() != null
                            ? SystemPromptResolver.resolve(stepDef.getSystemPrompt(), resourceResolver,
                                    "lo step '" + stepDef.getId() + "' della pipeline '" + def.getId() + "'")
                            : null;
                    steps.add(new ModelInferenceStep(stepDef.getId(), modelClient, stepDef.getModel(), schemaContent, systemPrompt));
                }
                case "field_validation" -> {
                    List<PathValidatorBinding> bindings = new ArrayList<>();
                    if (stepDef.getValidators() != null) {
                        for (FieldValidatorDefinition fvd : stepDef.getValidators()) {
                            Validator<String> validator = validatorRegistry.get(fvd.getValidator()).orElseThrow();
                            bindings.add(new PathValidatorBinding(new FieldPath(fvd.getField()), validator));
                        }
                    }
                    List<RequiredFieldSpec> requiredSpecs = Collections.emptyList();
                    if (lastInferenceSchema != null) {
                        requiredSpecs = RequiredFieldSpec.extractRequiredSpecs(lastInferenceSchema);
                    }
                    boolean guardsEnabled = environment.getProperty("orchestrator.guards.enabled", Boolean.class, true);
                    boolean coherenceEnabled = environment.getProperty("orchestrator.coherence.enabled", Boolean.class, true);
                    steps.add(new OutputValidationStep(stepDef.getId(), bindings, requiredSpecs, stepDef.getOnFailureDefinition(), guardsEnabled, coherenceEnabled));
                }
                case "tool" -> {
                    String toolName = stepDef.getTool();
                    steps.add(new ToolPipelineStep(stepDef.getId(), toolName, toolRegistry, stepDef.getArgs()));
                }
                case "decision" -> {
                    String model = stepDef.getModel();
                    if (model == null || model.isBlank()) {
                        model = environment.getProperty("ollama.reflex-model", DecisionPipelineStep.DEFAULT_REFLEX_MODEL);
                    }
                    String candidatesSource = stepDef.getCandidatesSource();
                    String targetField = stepDef.getTargetField();
                    String systemPrompt = stepDef.getSystemPrompt() != null
                            ? SystemPromptResolver.resolve(stepDef.getSystemPrompt(), resourceResolver,
                                    "lo step '" + stepDef.getId() + "' della pipeline '" + def.getId() + "'")
                            : null;
                    boolean shuffle = stepDef.getShuffleCandidates() != null ? stepDef.getShuffleCandidates() : false;
                    steps.add(new DecisionPipelineStep(stepDef.getId(), modelClient, model, candidatesSource, targetField, systemPrompt, shuffle, null));
                }
                default -> throw new IllegalStateException("Tipo step inatteso dopo la validazione: " + type);
            }
        }

        long maxDurationMs = environment.getProperty("orchestrator.pipeline.max-duration-ms", Long.class, 60000L);
        boolean includeSensitiveValues = environment.getProperty("orchestrator.logging.include-sensitive-values", Boolean.class, false);
        return new BasePipeline(docType, steps, maxDurationMs, def.getActions(), includeSensitiveValues);
    }

    private JsonNode parseSchemaJson(String content, String schemaPath) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new PipelineConfigurationException("Impossibile analizzare lo schema JSON: " + schemaPath, e);
        }
    }

    private String loadSchemaContent(String schemaPath) {
        String resolvedPath = schemaPath.strip();
        if (!resolvedPath.startsWith("classpath:") && !resolvedPath.startsWith("file:")) {
            resolvedPath = "classpath:" + (resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath);
        }

        Resource res = resourceResolver.getResource(resolvedPath);
        try (InputStream is = res.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PipelineConfigurationException("Impossibile caricare il contenuto dello schema JSON: " + schemaPath, e);
        }
    }
}
