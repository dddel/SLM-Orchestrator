package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.validation.RequiredFieldSpec;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineConfigurationException;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition.FieldValidatorDefinition;

/**
 * Test dei controlli stringenti Fail-Fast al bootstrap:
 * 1. Token matching per validatori noti (emailMittente, mittenteEmail, paeseLibano non deve matchare)
 * 2. Campo noto nello schema non coperto né esentato (fail-fast)
 * 3. Campo in unvalidated ma non esistente nello schema
 * 4. Campo sia in validators che in unvalidated (sovrapposizione non permessa)
 * 5. unvalidated dichiarato su step diverso da field_validation
 * 6. type: tool rifiutato con messaggio dedicato
 * 7. type sconosciuto (refuso) rifiutato con messaggio dedicato
 * 8. Schema inesistente, validatore sconosciuto, DocumentType duplicato, placeholder mancanti
 */
class PipelineBootstrapFailFastTest {

    private ValidatorRegistry validatorRegistry;
    private DefaultResourceLoader resourceLoader;
    private PipelineConfigValidator validator;

    @BeforeEach
    void setUp() {
        validatorRegistry = new ValidatorRegistry();
        resourceLoader = new DefaultResourceLoader();
        validator = new PipelineConfigValidator(validatorRegistry, new ToolRegistry(), resourceLoader);
    }

    @Test
    @DisplayName("Token matching: verifica correttezza prefissi, suffissi e non-matching di sottostringhe casuali")
    void testTokenMatchingRules() {
        // Prefissi e suffissi
        assertTrue(PipelineConfigValidator.matchesKnownValidator("emailMittente"));
        assertTrue(PipelineConfigValidator.matchesKnownValidator("mittenteEmail"));
        assertTrue(PipelineConfigValidator.matchesKnownValidator("partitaIva"));
        assertTrue(PipelineConfigValidator.matchesKnownValidator("partita_iva"));
        assertTrue(PipelineConfigValidator.matchesKnownValidator("fornitorePartitaIva"));
        assertTrue(PipelineConfigValidator.matchesKnownValidator("codiceFiscale"));
        assertTrue(PipelineConfigValidator.matchesKnownValidator("ibanAzienda"));

        // Non deve matchare parole contenenti sottostringhe casuali come 'iban' in 'paeseLibano' o 'email' in 'temaIlluminato'
        assertFalse(PipelineConfigValidator.matchesKnownValidator("paeseLibano"));
        assertFalse(PipelineConfigValidator.matchesKnownValidator("libano"));
        assertFalse(PipelineConfigValidator.matchesKnownValidator("descrizione"));
        assertFalse(PipelineConfigValidator.matchesKnownValidator("telefono"));
    }

    @Test
    @DisplayName("Fail-fast: type: tool rifiutato con messaggio esplicito di non supporto RF8")
    void failFastTypeToolRifiutato() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("FATTURA");

        StepDefinition toolStep = new StepDefinition();
        toolStep.setId("t1");
        toolStep.setType("tool");
        toolStep.setName("vies_tool");

        def.setSteps(List.of(toolStep));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("non è ancora supportato"), () -> "Messaggio effettivo: '" + ex.getMessage() + "'");
    }

    @Test
    @DisplayName("Fail-fast: type sconosciuto (refuso) rifiutato con messaggio dedicato")
    void failFastTypeSconosciuto() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("FATTURA");

        StepDefinition step = new StepDefinition();
        step.setId("s1");
        step.setType("model_infrence"); // refuso
        step.setModel("model-test");
        step.setSchema("schemas/fattura_info.json");

        def.setSteps(List.of(step));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("Tipo di step sconosciuto: 'model_infrence'"));
    }

    @Test
    @DisplayName("Fail-fast: campo con validatore noto non coperto né esentato in unvalidated")
    void failFastCampoNonCopertoNeEsentato() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("fattura_pipeline");
        def.setDocumentType("FATTURA");

        StepDefinition stepInference = new StepDefinition();
        stepInference.setId("inf");
        stepInference.setType("model_inference");
        stepInference.setModel("model-test");
        stepInference.setSchema("schemas/fattura_info.json");

        // Schema fattura ha: fornitore.partitaIva, cliente.codiceFiscale, iban
        // Qui copriamo solo partitaIva; cliente.codiceFiscale e iban restano scoperti
        StepDefinition stepValidation = new StepDefinition();
        stepValidation.setId("val");
        stepValidation.setType("field_validation");
        stepValidation.setValidators(List.of(
                new FieldValidatorDefinition("fornitore.partitaIva", "partita_iva")
        ));

        def.setSteps(List.of(stepInference, stepValidation));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("corrisponde a un validatore noto ma non è coperto in 'validators' né esentato in 'unvalidated'"));
    }

    @Test
    @DisplayName("Fail-fast: campo dichiarato sia in validators che in unvalidated genera errore")
    void failFastOverlapValidatorsEUnvalidated() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("anagrafica_pipeline");
        def.setDocumentType("ANAGRAFICA");

        StepDefinition stepInference = new StepDefinition();
        stepInference.setId("inf");
        stepInference.setType("model_inference");
        stepInference.setModel("model-test");
        stepInference.setSchema("schemas/anagrafica_documento.json");

        StepDefinition stepValidation = new StepDefinition();
        stepValidation.setId("val");
        stepValidation.setType("field_validation");
        stepValidation.setValidators(List.of(
                new FieldValidatorDefinition("codiceFiscale", "codice_fiscale")
        ));
        stepValidation.setUnvalidated(List.of("codiceFiscale"));

        def.setSteps(List.of(stepInference, stepValidation));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("non può essere contemporaneamente validato ed esentato in 'unvalidated'"));
    }

    @Test
    @DisplayName("Fail-fast: percorso in unvalidated inesistente nello schema JSON")
    void failFastUnvalidatedInesistenteNelloSchema() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition stepInference = new StepDefinition();
        stepInference.setId("inf");
        stepInference.setType("model_inference");
        stepInference.setModel("model-test");
        stepInference.setSchema("schemas/email_info.json");

        StepDefinition stepValidation = new StepDefinition();
        stepValidation.setId("val");
        stepValidation.setType("field_validation");
        stepValidation.setValidators(List.of(
                new FieldValidatorDefinition("email", "email")
        ));
        stepValidation.setUnvalidated(List.of("campoInesistente"));

        def.setSteps(List.of(stepInference, stepValidation));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("dichiarato in 'unvalidated' nello step 'val' non esiste nello schema JSON"));
    }

    @Test
    @DisplayName("Bootstrap: percorso in unvalidated esistente nello schema JSON passa la validazione")
    void testUnvalidatedEsistenteNelloSchemaValido() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition stepInference = new StepDefinition();
        stepInference.setId("inf");
        stepInference.setType("model_inference");
        stepInference.setModel("model-test");
        stepInference.setSchema("schemas/email_info.json");

        StepDefinition stepValidation = new StepDefinition();
        stepValidation.setId("val");
        stepValidation.setType("field_validation");
        stepValidation.setValidators(List.of(
                new FieldValidatorDefinition("email", "email")
        ));
        // 'oggetto' è un campo presente nello schema email_info.json
        stepValidation.setUnvalidated(List.of("oggetto"));

        def.setSteps(List.of(stepInference, stepValidation));

        assertDoesNotThrow(() -> validator.validateAll(List.of(def)));
    }

    @Test
    @DisplayName("Fail-fast: unvalidated dichiarato su uno step model_inference")
    void failFastUnvalidatedSuStepNonFieldValidation() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition stepInference = new StepDefinition();
        stepInference.setId("inf");
        stepInference.setType("model_inference");
        stepInference.setModel("model-test");
        stepInference.setSchema("schemas/email_info.json");
        stepInference.setUnvalidated(List.of("email"));

        def.setSteps(List.of(stepInference));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("La direttiva 'unvalidated' è ammessa solo su step di tipo 'field_validation'"));
    }

    @Test
    @DisplayName("Fail-fast: schema JSON inesistente blocca l'avvio")
    void failFastSchemaInesistente() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("FATTURA");

        StepDefinition step = new StepDefinition();
        step.setId("step1");
        step.setType("model_inference");
        step.setModel("model-test");
        step.setSchema("schemas/schema_che_non_esiste.json");
        def.setSteps(List.of(step));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("non trovato al percorso"));
    }

    @Test
    @DisplayName("Fail-fast: validatore con nome sconosciuto blocca l'avvio")
    void failFastValidatoreSconosciuto() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition stepInference = new StepDefinition();
        stepInference.setId("step_inf");
        stepInference.setType("model_inference");
        stepInference.setModel("model-test");
        stepInference.setSchema("schemas/email_info.json");

        StepDefinition stepValidation = new StepDefinition();
        stepValidation.setId("step_val");
        stepValidation.setType("field_validation");
        stepValidation.setValidators(List.of(
                new FieldValidatorDefinition("email", "validatore_inventato_inesistente")
        ));

        def.setSteps(List.of(stepInference, stepValidation));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("Validatore sconosciuto"));
    }

    @Test
    @DisplayName("Fail-fast: DocumentType duplicato blocca l'avvio")
    void failFastDocumentTypeDuplicato() {
        PipelineDefinition def1 = new PipelineDefinition();
        def1.setId("email_1");
        def1.setDocumentType("EMAIL");
        StepDefinition s1 = new StepDefinition();
        s1.setId("s1");
        s1.setType("model_inference");
        s1.setModel("m");
        s1.setSchema("schemas/email_info.json");
        StepDefinition v1 = new StepDefinition();
        v1.setId("v1");
        v1.setType("field_validation");
        v1.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        def1.setSteps(List.of(s1, v1));

        PipelineDefinition def2 = new PipelineDefinition();
        def2.setId("email_2");
        def2.setDocumentType("EMAIL");
        StepDefinition s2 = new StepDefinition();
        s2.setId("s2");
        s2.setType("model_inference");
        s2.setModel("m");
        s2.setSchema("schemas/email_info.json");
        StepDefinition v2 = new StepDefinition();
        v2.setId("v2");
        v2.setType("field_validation");
        v2.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        def2.setSteps(List.of(s2, v2));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def1, def2))
        );
        assertTrue(ex.getMessage().contains("DocumentType duplicato"));
    }

    @Test
    @DisplayName("Fail-fast: on_failure non ammesso su step model_inference")
    void failFastOnFailureSuModelInference() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("FATTURA");

        StepDefinition step = new StepDefinition();
        step.setId("step1");
        step.setType("model_inference");
        step.setModel("model-test");
        step.setSchema("schemas/fattura_info.json");
        step.setOnFailure(Map.of("retry_step", "step1", "max_retries", 2));
        def.setSteps(List.of(step));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("La policy 'on_failure' è ammessa solo su step di tipo 'field_validation'"));
    }

    @Test
    @DisplayName("Fail-fast: on_failure con retry_step inesistente o che non precede")
    void failFastOnFailureRetryStepNonPrecede() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition s1 = new StepDefinition();
        s1.setId("inf");
        s1.setType("model_inference");
        s1.setModel("model-test");
        s1.setSchema("schemas/email_info.json");

        StepDefinition s2 = new StepDefinition();
        s2.setId("val");
        s2.setType("field_validation");
        s2.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        s2.setOnFailure(Map.of("retry_step", "stepInesistente", "max_retries", 2));
        def.setSteps(List.of(s1, s2));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("non esiste oppure non precede lo step corrente"));
    }

    @Test
    @DisplayName("Fail-fast: on_failure con max_retries <= 0")
    void failFastOnFailureMaxRetriesNonValido() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition s1 = new StepDefinition();
        s1.setId("inf");
        s1.setType("model_inference");
        s1.setModel("model-test");
        s1.setSchema("schemas/email_info.json");

        StepDefinition s2 = new StepDefinition();
        s2.setId("val");
        s2.setType("field_validation");
        s2.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        s2.setOnFailure(Map.of("retry_step", "inf", "max_retries", 0));
        def.setSteps(List.of(s1, s2));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("deve essere maggiore o uguale a 1"));
    }

    @Test
    @DisplayName("Fail-fast: escalation cloud senza autorizzazione esplicita allow_cloud_escalation: true")
    void failFastEscalationCloudSenzaFlag() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipeline");
        def.setDocumentType("EMAIL");

        StepDefinition s1 = new StepDefinition();
        s1.setId("inf");
        s1.setType("model_inference");
        s1.setModel("model-test");
        s1.setSchema("schemas/email_info.json");

        StepDefinition s2 = new StepDefinition();
        s2.setId("val");
        s2.setType("field_validation");
        s2.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        s2.setOnFailure(Map.of(
                "retry_step", "inf",
                "max_retries", 1,
                "escalate_to", "   "
        ));
        def.setSteps(List.of(s1, s2));

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(def))
        );
        assertTrue(ex.getMessage().contains("escalate_to") && ex.getMessage().contains("non può essere vuota"));
    }

    @Test
    @DisplayName("Fail-fast: placeholder non risolti nel file YAML bloccano il caricamento")
    void failFastPlaceholderMancante() {
        MockEnvironment envWithoutProperties = new MockEnvironment();
        YamlPipelineLoader loader = new YamlPipelineLoader(envWithoutProperties, validatorRegistry, validator);
        ModelClient modelClient = mock(ModelClient.class);

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                () -> loader.loadPipelines(modelClient)
        );
        assertTrue(ex.getMessage().contains("Impossibile risolvere uno o più placeholder"));
    }

    @Test
    @DisplayName("Required validation: controllo vuoto/blank su radice, annidati e array con rispetto antenati opzionali")
    void testRequiredValidationBehavior() {
        // Schema sintetico con:
        // - id (required radice)
        // - opzionaleObj (opzionale) -> subRequired (required in opzionaleObj)
        // - voci (array) -> items con nome (required in item)
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        tools.jackson.databind.JsonNode schema = mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string" },
                    "opzionaleObj": {
                      "type": "object",
                      "properties": {
                        "subRequired": { "type": "string" }
                      },
                      "required": ["subRequired"]
                    },
                    "voci": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "nome": { "type": "string" }
                        },
                        "required": ["nome"]
                      }
                    }
                  },
                  "required": ["id"]
                }
                """);

        List<RequiredFieldSpec> specs =
                RequiredFieldSpec.extractRequiredSpecs(schema);

        OutputValidationStep step =
                new OutputValidationStep("val", List.of(), specs);

        // Caso 1: id vuoto -> errore
        PipelineContext ctx1 =
                new PipelineContext("in", DocumentType.ALTRO);
        ctx1.setModelOutput("{\"id\": \"   \"}");
        var res1 = step.execute(ctx1);
        assertFalse(res1.isValid());
        assertTrue(res1.errors().get(0).contains("presente ma vuoto"));

        // Caso 2: genitore opzionale assente -> subRequired NON deve fallire
        PipelineContext ctx2 =
                new PipelineContext("in", DocumentType.ALTRO);
        ctx2.setModelOutput("{\"id\": \"ID-123\"}");
        var res2 = step.execute(ctx2);
        assertTrue(res2.isValid());

        // Caso 3: genitore opzionale presente ma subRequired vuoto -> errore
        PipelineContext ctx3 =
                new PipelineContext("in", DocumentType.ALTRO);
        ctx3.setModelOutput("{\"id\": \"ID-123\", \"opzionaleObj\": {\"subRequired\": \"\"}}");
        var res3 = step.execute(ctx3);
        assertFalse(res3.isValid());
        assertTrue(res3.errors().get(0).contains("presente ma vuoto"));

        // Caso 4: array con elemento required vuoto -> errore
        PipelineContext ctx4 =
                new PipelineContext("in", DocumentType.ALTRO);
        ctx4.setModelOutput("{\"id\": \"ID-123\", \"voci\": [{\"nome\": \"Item 1\"}, {\"nome\": \"  \"}]}");
        var res4 = step.execute(ctx4);
        assertFalse(res4.isValid());
        assertTrue(res4.errors().get(0).contains("presente ma vuoto"));
    }

    @Test
    @DisplayName("Fail-fast: cartella esterna specificata in orchestrator.pipelines.dir inesistente lancia PipelineConfigurationException")
    void failFastCartellaEsternaInesistenteLanciaEccezione() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("orchestrator.pipelines.dir", "/percorso/inesistente/delle/pipeline");
        YamlPipelineLoader loader = new YamlPipelineLoader(env, validatorRegistry, validator);

        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                loader::loadDefinitions
        );
        assertTrue(ex.getMessage().contains("/percorso/inesistente/delle/pipeline"));
    }

    @Test
    @DisplayName("Bootstrap: caricamento e validazione della pipeline fattura con validatore composito codice_fiscale_o_partita_iva")
    void testCaricamentoPipelineFatturaConValidatoreComposito() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ollama.model", "qwen2.5-generic:latest");
        env.setProperty("ollama.email-model", "qwen2.5-email:latest");
        env.setProperty("ollama.invoice-model", "qwen2.5-fattura:latest");
        env.setProperty("ollama.anagrafica-model", "qwen2.5-anagrafica:latest");
        YamlPipelineLoader loader = new YamlPipelineLoader(env, validatorRegistry, validator);

        List<PipelineDefinition> definitions = loader.loadDefinitions();
        var fatturaDef = definitions.stream()
                .filter(d -> "fattura_pipeline".equals(d.getId()))
                .findFirst();
        assertTrue(fatturaDef.isPresent());

        // La validazione al bootstrap non deve sollevare eccezioni
        assertDoesNotThrow(() -> validator.validateAll(List.of(fatturaDef.get())));
    }

    @Test
    @DisplayName("Fail-fast: timeout_ms <= 0 o > 120000 e max_retries < 0 o > 5 in step tool / actions rifiutati")
    void failFastToolExecutionPolicyInvalidBounds() {
        ToolRegistry reg = new ToolRegistry();
        PipelineConfigValidator v = new PipelineConfigValidator(validatorRegistry, reg, resourceLoader);

        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_test");
        def.setDocumentType("EMAIL");

        StepDefinition toolStep = new StepDefinition();
        toolStep.setId("roles_lookup");
        toolStep.setType("tool");
        toolStep.setTool("company_roles_lookup");
        toolStep.setTimeoutMs(0L);
        def.setSteps(List.of(toolStep));

        PipelineConfigurationException ex1 = assertThrows(
                PipelineConfigurationException.class,
                () -> v.validateAll(List.of(def))
        );
        assertTrue(ex1.getMessage().contains("timeout_ms"));

        toolStep.setTimeoutMs(2000L);
        toolStep.setMaxRetries(6);
        PipelineConfigurationException ex2 = assertThrows(
                PipelineConfigurationException.class,
                () -> v.validateAll(List.of(def))
        );
        assertTrue(ex2.getMessage().contains("max_retries"));

        toolStep.setMaxRetries(1);
        toolStep.setRetryBackoffMs(-10L);
        PipelineConfigurationException ex3 = assertThrows(
                PipelineConfigurationException.class,
                () -> v.validateAll(List.of(def))
        );
        assertTrue(ex3.getMessage().contains("retry_backoff_ms"));
    }

    @Test
    @DisplayName("Fail-fast: validazione dichiarativa di merge_fields, inject_context, catalog_validation e coherence_checks")
    void failFastDeclarativeStepPropertiesValidation() {
        // 1. merge_fields con campo inesistente nello schema JSON
        PipelineDefinition defMerge = new PipelineDefinition();
        defMerge.setId("merge_invalid_pipe");
        defMerge.setDocumentType("ALTRO");
        StepDefinition infMerge = new StepDefinition();
        infMerge.setId("inf1");
        infMerge.setType("model_inference");
        infMerge.setModel("m");
        infMerge.setSchema("schemas/altro_info.json");
        infMerge.setMergeFields(List.of("campoCheNonEsiste"));
        defMerge.setSteps(List.of(infMerge));

        PipelineConfigurationException exMerge = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(defMerge))
        );
        assertTrue(exMerge.getMessage().contains("merge_fields"));

        // 2. inject_context con chiave o valore vuoto
        StepDefinition infInject = new StepDefinition();
        infInject.setId("inf2");
        infInject.setType("model_inference");
        infInject.setModel("m");
        infInject.setSchema("schemas/altro_info.json");
        infInject.setInjectContext(Map.of("tag_valido", "   "));
        defMerge.setSteps(List.of(infInject));

        PipelineConfigurationException exInject = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(defMerge))
        );
        assertTrue(exInject.getMessage().contains("inject_context"));

        // 3. catalog_validation con campo inesistente nello schema JSON
        StepDefinition infValid = new StepDefinition();
        infValid.setId("inf3");
        infValid.setType("model_inference");
        infValid.setModel("m");
        infValid.setSchema("schemas/altro_info.json");

        StepDefinition valCat = new StepDefinition();
        valCat.setId("val_cat");
        valCat.setType("field_validation");
        valCat.setCatalogValidation(new StepDefinition.CatalogValidationDefinition("company_roles", List.of("campoInesistente")));
        defMerge.setSteps(List.of(infValid, valCat));

        PipelineConfigurationException exCat = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(defMerge))
        );
        assertTrue(exCat.getMessage().contains("catalog_validation.fields"));

        // 4. coherence_checks con nome di controllo non registrato
        StepDefinition valCoh = new StepDefinition();
        valCoh.setId("val_coh");
        valCoh.setType("field_validation");
        valCoh.setCoherenceChecks(List.of("controllo_inventato"));
        defMerge.setSteps(List.of(infValid, valCoh));

        PipelineConfigurationException exCoh = assertThrows(
                PipelineConfigurationException.class,
                () -> validator.validateAll(List.of(defMerge))
        );
        assertTrue(exCoh.getMessage().contains("coherence_checks"));
    }
}
