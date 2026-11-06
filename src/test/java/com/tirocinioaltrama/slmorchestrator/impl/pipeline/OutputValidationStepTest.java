package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldPath;
import com.tirocinioaltrama.slmorchestrator.core.validation.RequiredFieldSpec;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.Validator;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep.PathValidatorBinding;

class OutputValidationStepTest {

    @Test
    @DisplayName("D2: Limite noto - elementi parziali nell'array non rilevati da required")
    void requiredInsideArrayPartialElementsNotDetectedKnownLimitation() {
        // NOTA ARCHITETTURALE (cfr. DOCUMENTAZIONE_ARCHITETTURA.md §10):
        // Con l'implementazione attuale di FieldPath.extractNodes, se almeno un elemento
        // dell'array contiene il campo required, nodes.isEmpty() risulta false e l'assenza
        // del campo negli altri elementi non viene rilevata.
        // Se e quando questo limite verrà corretto, questo test dovrà essere invertito
        // aspettandosi che resPartial sia NON valido (!resPartial.isValid()).
        FieldPath vociPath = new FieldPath("voci");
        FieldPath descPath = new FieldPath("voci.descrizione");
        RequiredFieldSpec spec = new RequiredFieldSpec(descPath, List.of(vociPath), false);

        OutputValidationStep step = new OutputValidationStep("val", List.of(), List.of(spec));

        // Array presente dove il primo elemento ha 'descrizione' e il secondo no
        PipelineContext ctxPartial = new PipelineContext("test", DocumentType.FATTURA);
        ctxPartial.setModelOutput("{\"voci\": [{\"descrizione\": \"Articolo 1\"}, {\"prezzo\": 20}]}");
        ValidationResult resPartial = step.execute(ctxPartial);
        assertTrue(resPartial.isValid(), "Limite noto: elementi parziali nell'array non producono errore se almeno uno ha il campo");
    }

    @Test
    @DisplayName("D2: Controllo required su array - tutti gli elementi privi del campo emettono REQUIRED_MISSING senza indice")
    void testRequiredInsideArrayAllElementsMissingEmitsError() {
        FieldPath vociPath = new FieldPath("voci");
        FieldPath descPath = new FieldPath("voci.descrizione");
        RequiredFieldSpec spec = new RequiredFieldSpec(descPath, List.of(vociPath), false);

        OutputValidationStep step = new OutputValidationStep("val", List.of(), List.of(spec));

        // Array presente con tutti gli elementi privi del campo required
        // FieldPath.extractNodes restituisce lista vuota -> REQUIRED_MISSING
        PipelineContext ctxMissing = new PipelineContext("test", DocumentType.FATTURA);
        ctxMissing.setModelOutput("{\"voci\": [{\"prezzo\": 10}, {\"prezzo\": 20}]}");
        ValidationResult resMissing = step.execute(ctxMissing);

        assertFalse(resMissing.isValid());
        assertEquals(1, resMissing.fieldErrors().size());
        FieldError err = resMissing.fieldErrors().get(0);
        assertEquals(ValidationErrorCode.REQUIRED_MISSING, err.code());
        // Il path nel FieldError contiene il percorso originale configurato, senza indice di array
        assertEquals("voci.descrizione", err.path());

        // Array presente dove un elemento ha descrizione null
        PipelineContext ctxNull = new PipelineContext("test", DocumentType.FATTURA);
        ctxNull.setModelOutput("{\"voci\": [{\"descrizione\": null}]}");
        ValidationResult resNull = step.execute(ctxNull);
        assertFalse(resNull.isValid());
        assertEquals(ValidationErrorCode.REQUIRED_NULL, resNull.fieldErrors().get(0).code());
        assertEquals("voci.descrizione", resNull.fieldErrors().get(0).path());
    }

    @Test
    @DisplayName("C4.4: Campo required presente con stringa vuota emette solo REQUIRED_EMPTY e non VALUE_EMPTY")
    void testRequiredEmptyStringEmitsOnlyRequiredEmptyNotValueEmpty() {
        // Campo 'codiceFiscale' configurato sia come required sia con validatore
        FieldPath cfPath = new FieldPath("codiceFiscale");
        RequiredFieldSpec spec = new RequiredFieldSpec(cfPath, Collections.emptyList(), false);

        ValidatorRegistry registry = new ValidatorRegistry();
        Validator<String> cfValidator = registry.get("codice_fiscale").orElseThrow();
        PathValidatorBinding binding = new PathValidatorBinding(cfPath, cfValidator);

        OutputValidationStep step = new OutputValidationStep("val", List.of(binding), List.of(spec));

        PipelineContext context = new PipelineContext("test", DocumentType.ANAGRAFICA);
        context.setModelOutput("{\"codiceFiscale\": \"\"}");

        ValidationResult res = step.execute(context);
        assertFalse(res.isValid());

        List<FieldError> errors = res.fieldErrors();
        assertEquals(1, errors.size(), "Deve essere emesso un solo errore per stringa vuota");
        FieldError error = errors.get(0);
        assertEquals(ValidationErrorCode.REQUIRED_EMPTY, error.code(),
                "L'errore emesso deve essere REQUIRED_EMPTY dal controllo required");
        // Verifica che il validatore di campo NON sia stato invocato (non c'è VALUE_EMPTY)
        boolean hasValueEmpty = errors.stream().anyMatch(e -> e.code() == ValidationErrorCode.VALUE_EMPTY);
        assertFalse(hasValueEmpty, "VALUE_EMPTY non deve essere emesso perché extractValues ignora le stringhe vuote");
    }

    @Test
    @DisplayName("Costruttori accessori ed esecuzione con validatori legacy")
    void testConstructorsAndLegacyValidators() {
        OutputValidationStep defaultStep = new OutputValidationStep();
        assertEquals("output_validation", defaultStep.getId());
        assertEquals("OutputValidationStep", defaultStep.getName());
        assertTrue(defaultStep.isGuardsEnabled());
        assertTrue(defaultStep.isCoherenceEnabled());

        OutputValidationStep stepWithLegacy = new OutputValidationStep(OutputValidationStep.defaultValidators());
        PipelineContext ctx = new PipelineContext("test", DocumentType.EMAIL);
        ctx.setModelOutput("{\"email\": \"invalid-email\"}");
        ValidationResult res = stepWithLegacy.execute(ctx);
        assertFalse(res.isValid());
        assertTrue(res.errors().stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    @DisplayName("Esecuzione con output JSON malformato o nullo fallisce su jsonCheck")
    void testMalformedJsonOutput() {
        OutputValidationStep step = new OutputValidationStep();
        PipelineContext ctxNull = new PipelineContext("test", DocumentType.EMAIL);
        ctxNull.setModelOutput(null);
        ValidationResult resNull = step.execute(ctxNull);
        assertFalse(resNull.isValid());

        PipelineContext ctxBad = new PipelineContext("test", DocumentType.EMAIL);
        ctxBad.setModelOutput("{bad_json");
        ValidationResult resBad = step.execute(ctxBad);
        assertFalse(resBad.isValid());
    }
}
