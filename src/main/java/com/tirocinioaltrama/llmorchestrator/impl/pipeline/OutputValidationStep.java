package com.tirocinioaltrama.llmorchestrator.impl.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.llmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.llmorchestrator.core.validation.FieldPath;
import com.tirocinioaltrama.llmorchestrator.core.validation.JsonOutputValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.RequiredFieldSpec;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.core.validation.Validator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.CodiceFiscaleValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.EmailFormatValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.IbanValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.PartitaIvaValidator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Passo di validazione dell'output JSON prodotto dal modello:
 * 1. Verifica la conformità sintattica JSON.
 * 2. Valida la presenza e non-vacuità dei campi 'required' dello schema precalcolati al bootstrap
 *    (se gli antenati opzionali sono assenti o nulli, il controllo viene opportunamente saltato).
 * 3. Valida deterministicamente i campi tramite percorsi qualificati (FieldPath) o chiavi legacy.
 * 4. Rileva se un errore algebrico è presente verbatim nel documento sorgente per abilitare il cortocircuito dei retry.
 */
public class OutputValidationStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(OutputValidationStep.class);

    private final String id;
    private final JsonOutputValidator jsonValidator;
    private final ObjectMapper objectMapper;
    private final List<PathValidatorBinding> pathBindings;
    private final List<RequiredFieldSpec> requiredSpecs;
    private final Map<String, java.util.function.Function<String, ValidationResult>> legacyFieldValidators;
    private final com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition onFailureDefinition;
    private final boolean guardsEnabled;
    private final boolean coherenceEnabled;

    public record PathValidatorBinding(FieldPath path, Validator<String> validator) {}

    public OutputValidationStep() {
        this(defaultValidators());
    }

    public OutputValidationStep(Map<String, java.util.function.Function<String, ValidationResult>> fieldValidators) {
        this(null, new ObjectMapper(), fieldValidators, Collections.emptyList(), Collections.emptyList());
    }

    public OutputValidationStep(ObjectMapper objectMapper, Map<String, java.util.function.Function<String, ValidationResult>> fieldValidators) {
        this(null, objectMapper, fieldValidators, Collections.emptyList(), Collections.emptyList());
    }

    public OutputValidationStep(String id, List<PathValidatorBinding> pathBindings) {
        this(id, new ObjectMapper(), Collections.emptyMap(), pathBindings, Collections.emptyList());
    }

    public OutputValidationStep(String id, List<PathValidatorBinding> pathBindings, List<RequiredFieldSpec> requiredSpecs) {
        this(id, new ObjectMapper(), Collections.emptyMap(), pathBindings, requiredSpecs, null);
    }

    public OutputValidationStep(String id, List<PathValidatorBinding> pathBindings, List<RequiredFieldSpec> requiredSpecs, com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition onFailureDefinition) {
        this(id, new ObjectMapper(), Collections.emptyMap(), pathBindings, requiredSpecs, onFailureDefinition, true, true);
    }

    public OutputValidationStep(String id, List<PathValidatorBinding> pathBindings, List<RequiredFieldSpec> requiredSpecs, com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition onFailureDefinition, boolean guardsEnabled, boolean coherenceEnabled) {
        this(id, new ObjectMapper(), Collections.emptyMap(), pathBindings, requiredSpecs, onFailureDefinition, guardsEnabled, coherenceEnabled);
    }

    public OutputValidationStep(
            String id,
            ObjectMapper objectMapper,
            Map<String, java.util.function.Function<String, ValidationResult>> legacyFieldValidators,
            List<PathValidatorBinding> pathBindings,
            List<RequiredFieldSpec> requiredSpecs
    ) {
        this(id, objectMapper, legacyFieldValidators, pathBindings, requiredSpecs, null, true, true);
    }

    public OutputValidationStep(
            String id,
            ObjectMapper objectMapper,
            Map<String, java.util.function.Function<String, ValidationResult>> legacyFieldValidators,
            List<PathValidatorBinding> pathBindings,
            List<RequiredFieldSpec> requiredSpecs,
            com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition onFailureDefinition
    ) {
        this(id, objectMapper, legacyFieldValidators, pathBindings, requiredSpecs, onFailureDefinition, true, true);
    }

    public OutputValidationStep(
            String id,
            ObjectMapper objectMapper,
            Map<String, java.util.function.Function<String, ValidationResult>> legacyFieldValidators,
            List<PathValidatorBinding> pathBindings,
            List<RequiredFieldSpec> requiredSpecs,
            com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition onFailureDefinition,
            boolean guardsEnabled,
            boolean coherenceEnabled
    ) {
        this.id = id != null ? id : "output_validation";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.jsonValidator = new JsonOutputValidator(this.objectMapper);
        this.legacyFieldValidators = legacyFieldValidators != null ? Collections.unmodifiableMap(new LinkedHashMap<>(legacyFieldValidators)) : Map.of();
        this.pathBindings = pathBindings != null ? List.copyOf(pathBindings) : List.of();
        this.requiredSpecs = requiredSpecs != null ? List.copyOf(requiredSpecs) : List.of();
        this.onFailureDefinition = onFailureDefinition;
        this.guardsEnabled = guardsEnabled;
        this.coherenceEnabled = coherenceEnabled;
    }

    public boolean isGuardsEnabled() {
        return guardsEnabled;
    }

    public boolean isCoherenceEnabled() {
        return coherenceEnabled;
    }

    public com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition getOnFailureDefinition() {
        return onFailureDefinition;
    }

    public static Map<String, java.util.function.Function<String, ValidationResult>> defaultValidators() {
        Map<String, java.util.function.Function<String, ValidationResult>> map = new LinkedHashMap<>();
        EmailFormatValidator emailValidator = new EmailFormatValidator();
        CodiceFiscaleValidator cfValidator = new CodiceFiscaleValidator();
        PartitaIvaValidator pivaValidator = new PartitaIvaValidator();
        IbanValidator ibanValidator = new IbanValidator();

        map.put("email", emailValidator::validate);
        map.put("codiceFiscale", cfValidator::validate);
        map.put("partitaIva", pivaValidator::validate);
        map.put("iban", ibanValidator::validate);
        return map;
    }

    @Override
    public String getName() {
        return "OutputValidationStep";
    }

    public String getId() {
        return id;
    }

    @Override
    public ValidationResult execute(PipelineContext context) {
        String jsonOutput = context.getModelOutput();
        ValidationResult jsonCheck = jsonValidator.validate(jsonOutput);
        if (!jsonCheck.isValid()) {
            return jsonCheck;
        }

        List<String> errors = new ArrayList<>(jsonCheck.errors());
        List<String> warnings = new ArrayList<>(jsonCheck.warnings());
        List<FieldError> fieldErrors = new ArrayList<>(jsonCheck.fieldErrors());

        try {
            JsonNode root = objectMapper.readTree(jsonOutput);
            String payload = context.getSanitizedInput() != null ? context.getSanitizedInput() : (context.getRawInput() != null ? context.getRawInput() : "");

            // 0. Applicazione delle guardie simboliche deterministiche (G1, G2, G3, G4, Evidenza Sesso, Date) se abilitate
            if (guardsEnabled) {
                boolean modified = com.tirocinioaltrama.llmorchestrator.impl.pipeline.guard.PostProcessingGuards.applyGuards(root, payload, context.getDocumentType());
                if (modified) {
                    try {
                        context.setModelOutput(objectMapper.writeValueAsString(root));
                    } catch (Exception e) {
                        log.debug("Impossibile riserializzare JSON normalizzato: {}", e.getMessage());
                    }
                }
            }

            // 1. Controllo dei campi 'required' precalcolati al bootstrap
            if (!requiredSpecs.isEmpty()) {
                validateRequiredFields(root, errors, fieldErrors);
            }

            // 2. Validazione tramite path bindings espliciti (nuovo flusso dichiarativo YAML)
            if (!pathBindings.isEmpty()) {
                String rawPayload = context.getRawInput() != null ? context.getRawInput() : payload;
                String rawPayloadNoSpace = rawPayload.replaceAll("\\s+", "").toLowerCase();

                for (PathValidatorBinding binding : pathBindings) {
                    List<String> values = binding.path().extractValues(root);
                    for (String val : values) {
                        ValidationResult res = binding.validator().validate(val);
                        if (!res.isValid()) {
                            String valNoSpace = val != null ? val.replaceAll("\\s+", "").toLowerCase() : "";
                            boolean verbatimInSource = !valNoSpace.isBlank()
                                    && (rawPayload.toLowerCase().contains(val.strip().toLowerCase()) || rawPayloadNoSpace.contains(valNoSpace));

                            if (!res.fieldErrors().isEmpty()) {
                                for (FieldError fe : res.fieldErrors()) {
                                    boolean isAlgebraic = (fe.code() == ValidationErrorCode.CHECKSUM_INVALID)
                                            || (fe.code() == ValidationErrorCode.LENGTH_INVALID && isAlgebraicField(binding.path().getOriginalPath()));

                                    if (isAlgebraic && verbatimInSource) {
                                        String note = " [Dato invalido presente letteralmente nel testo sorgente: retry omesso]";
                                        FieldError sourceFe = new FieldError(binding.path().getOriginalPath(), fe.validatorName(), ValidationErrorCode.SOURCE_CHECKSUM_INVALID, fe.message() + note);
                                        fieldErrors.add(sourceFe);
                                        errors.add(fe.message() + note);
                                    } else {
                                        fieldErrors.add(new FieldError(binding.path().getOriginalPath(), fe.validatorName(), fe.code(), fe.message()));
                                        errors.add(fe.message());
                                    }
                                }
                            } else {
                                fieldErrors.add(new FieldError(binding.path().getOriginalPath(), "field", ValidationErrorCode.FORMAT_INVALID, String.join("; ", res.errors())));
                                errors.addAll(res.errors());
                            }
                        }
                    }
                }
            }

            // 3. Validazione di coerenza anagrafica (V1: Layout UE, V2: CF Cross-Field, Date, Parole Intere) se abilitata
            if (coherenceEnabled && context.getDocumentType() == com.tirocinioaltrama.llmorchestrator.core.DocumentType.ANAGRAFICA) {
                var coherenceRes = com.tirocinioaltrama.llmorchestrator.impl.pipeline.guard.AnagraficaCoherenceValidator.validate(root, payload);
                if (!coherenceRes.isValid()) {
                    for (var violation : coherenceRes.violations()) {
                        errors.add(violation.errorMessage());
                        fieldErrors.add(new FieldError(violation.path(), "coherence", ValidationErrorCode.FIELD_SWAP_DETECTED, violation.errorMessage()));
                    }
                }
            }

            // 4. Validazione legacy (se presente)
            if (!legacyFieldValidators.isEmpty()) {
                for (Map.Entry<String, java.util.function.Function<String, ValidationResult>> entry : legacyFieldValidators.entrySet()) {
                    String fieldName = entry.getKey();
                    java.util.function.Function<String, ValidationResult> validatorFn = entry.getValue();
                    validateNodeRecursively(root, fieldName, validatorFn, errors, fieldErrors);
                }
            }

        } catch (Exception e) {
            log.warn("Errore durante l'ispezione dell'output JSON per la validazione: {}", e.getMessage());
            String err = "Impossibile ispezionare i campi per la validazione deterministica: " + e.getMessage();
            errors.add(err);
            fieldErrors.add(new FieldError("$", "json", ValidationErrorCode.JSON_SYNTAX_INVALID, err));
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(false, errors, warnings, fieldErrors);
        }
        return warnings.isEmpty() ? ValidationResult.valid() : ValidationResult.withWarnings(warnings);
    }

    private static boolean isAlgebraicField(String path) {
        if (path == null) {
            return false;
        }
        String p = path.toLowerCase();
        return p.contains("partitaiva") || p.contains("codicefiscale") || p.contains("iban");
    }

    private void validateRequiredFields(JsonNode root, List<String> errors, List<FieldError> fieldErrors) {
        for (RequiredFieldSpec spec : requiredSpecs) {
            // Se uno qualunque degli antenati opzionali è assente o null, il ramo non è attivo
            boolean branchActive = true;
            for (FieldPath ancestor : spec.ancestorPaths()) {
                if (!ancestor.existsAndNonNull(root)) {
                    branchActive = false;
                    break;
                }
            }
            if (!branchActive) {
                continue;
            }

            // Estrai i nodi effettivi al percorso required
            List<JsonNode> nodes = spec.path().extractNodes(root);
            if (nodes.isEmpty()) {
                String msg = "Il campo obbligatorio '" + spec.path().getOriginalPath() + "' è mancante nell'output del modello.";
                errors.add(msg);
                fieldErrors.add(new FieldError(spec.path().getOriginalPath(), "required", ValidationErrorCode.REQUIRED_MISSING, msg));
                continue;
            }

            for (JsonNode n : nodes) {
                if (n == null || n.isNull()) {
                    if (!spec.nullable()) {
                        String msg = "Il campo obbligatorio '" + spec.path().getOriginalPath() + "' è null nell'output del modello.";
                        errors.add(msg);
                        fieldErrors.add(new FieldError(spec.path().getOriginalPath(), "required", ValidationErrorCode.REQUIRED_NULL, msg));
                    }
                } else if (n.isTextual()) {
                    String strVal = n.asString();
                    if (strVal == null || strVal.isBlank()) {
                        String msg = "Il campo obbligatorio '" + spec.path().getOriginalPath() + "' è presente ma vuoto nell'output del modello.";
                        errors.add(msg);
                        fieldErrors.add(new FieldError(spec.path().getOriginalPath(), "required", ValidationErrorCode.REQUIRED_EMPTY, msg));
                    }
                }
            }
        }
    }

    private void validateNodeRecursively(
            JsonNode node,
            String fieldName,
            java.util.function.Function<String, ValidationResult> validatorFn,
            List<String> errors,
            List<FieldError> fieldErrors
    ) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            if (node.hasNonNull(fieldName)) {
                JsonNode child = node.get(fieldName);
                if (child.isTextual() || child.isValueNode()) {
                    String val = child.asString();
                    if (val != null && !val.isBlank()) {
                        ValidationResult res = validatorFn.apply(val);
                        if (!res.isValid()) {
                            errors.addAll(res.errors());
                            if (!res.fieldErrors().isEmpty()) {
                                fieldErrors.addAll(res.fieldErrors());
                            } else {
                                fieldErrors.add(new FieldError(fieldName, "legacy", ValidationErrorCode.FORMAT_INVALID, String.join("; ", res.errors())));
                            }
                        }
                    }
                }
            }

            node.forEachEntry((key, childNode) -> {
                if (childNode.isObject() || childNode.isArray()) {
                    validateNodeRecursively(childNode, fieldName, validatorFn, errors, fieldErrors);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                validateNodeRecursively(element, fieldName, validatorFn, errors, fieldErrors);
            }
        }
    }
}
