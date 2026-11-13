package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldPath;
import com.tirocinioaltrama.slmorchestrator.core.validation.field.EmailFormatValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep.PathValidatorBinding;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.OnFailureDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.tool.CompanyRolesTool;

/**
 * Verifica che lo stato transitorio del retry (samplingOptions e overrideModelName)
 * venga isolato allo step soggetto a retry e non contamini gli step di inferenza successivi.
 */
class PipelineRetryStateIsolationTest {

    @Test
    @DisplayName("T1.1: La temperatura incrementata nel retry del primo step non contamina il secondo step di inferenza")
    void retryTemperatureDoesNotLeakToSubsequentInferenceStep() {
        List<ModelRequest> capturedRequests = new ArrayList<>();
        String[] responses = {
                "{\"mittente\":\"Mario Rossi\",\"email\":\"email-invalida\"}",
                "{\"mittente\":\"Mario Rossi\",\"email\":\"mario.rossi@example.com\"}",
                "{\"destinatario\":\"Stefano Vena\",\"ruoloDestinatario\":\"Direttore\",\"motivoInstradamento\":\"Preventivo\"}"
        };

        ModelClient recordingClient = request -> {
            int idx = capturedRequests.size();
            capturedRequests.add(request);
            return new ModelResponse(responses[idx], 25);
        };

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new CompanyRolesTool());

        ModelInferenceStep emailInference = new ModelInferenceStep(
                "email_inference", recordingClient, "base-3b", "{}", "System step 1"
        );
        OnFailureDefinition retryDef = OnFailureDefinition.fromMap(
                Map.of("retry_step", "email_inference", "max_retries", 2, "feedback", true)
        );
        OutputValidationStep emailValidation = new OutputValidationStep(
                "email_validation",
                List.of(new PathValidatorBinding(FieldPath.of("email"), new EmailFormatValidator())),
                List.of(),
                retryDef
        );
        ToolPipelineStep rolesLookup = new ToolPipelineStep(
                "roles_lookup", CompanyRolesTool.TOOL_NAME, toolRegistry, Map.of()
        );
        ModelInferenceStep recipientInference = new ModelInferenceStep(
                "recipient_inference", recordingClient, "base-3b", "{}", "System step 2"
        );
        OutputValidationStep recipientValidation = new OutputValidationStep(
                "recipient_validation",
                List.of(),
                List.of(),
                OnFailureDefinition.fromMap(
                        Map.of("retry_step", "recipient_inference", "max_retries", 2, "feedback", true)
                )
        );

        BasePipeline pipeline = new BasePipeline(
                DocumentType.EMAIL,
                List.of(emailInference, emailValidation, rolesLookup, recipientInference, recipientValidation)
        );

        PipelineContext context = new PipelineContext("Contattatemi a mario.rossi@example.com", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success(), "La pipeline deve convergere a successo");
        assertEquals(3, capturedRequests.size());

        // Chiamata 1: primo tentativo step 1 -> options null
        assertNull(capturedRequests.get(0).options());
        assertEquals("base-3b", capturedRequests.get(0).model());

        // Chiamata 2: retry #1 step 1 -> temperature 0.2
        assertEquals(Map.of("temperature", 0.2), capturedRequests.get(1).options());
        assertEquals("base-3b", capturedRequests.get(1).model());

        // Chiamata 3: primo tentativo step 2 (recipient_inference) -> options deve essere tornato null!
        assertNull(capturedRequests.get(2).options(),
                "Le opzioni di campionamento del retry non devono propagarsi allo step successivo");
        assertEquals("base-3b", capturedRequests.get(2).model());
        assertFalse(capturedRequests.get(2).userPrompt().contains("<feedback>"));
    }

    @Test
    @DisplayName("T1.2: Il modello di escalation usato nel primo step non contamina il secondo step di inferenza")
    void escalationModelDoesNotLeakToSubsequentInferenceStep() {
        List<ModelRequest> capturedRequests = new ArrayList<>();
        String[] responses = {
                "{\"mittente\":\"Mario Rossi\",\"email\":\"email-invalida\"}",
                "{\"mittente\":\"Mario Rossi\",\"email\":\"mario.rossi@example.com\"}",
                "{\"destinatario\":\"Stefano Vena\",\"ruoloDestinatario\":\"Direttore\",\"motivoInstradamento\":\"Preventivo\"}"
        };

        ModelClient recordingClient = request -> {
            int idx = capturedRequests.size();
            capturedRequests.add(request);
            return new ModelResponse(responses[idx], 25);
        };

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new CompanyRolesTool());

        ModelInferenceStep emailInference = new ModelInferenceStep(
                "email_inference", recordingClient, "base-3b", "{}", "System step 1"
        );
        OnFailureDefinition escalateDef = OnFailureDefinition.fromMap(
                Map.of("retry_step", "email_inference", "max_retries", 0, "feedback", true, "escalate_to", "escalation-7b")
        );
        OutputValidationStep emailValidation = new OutputValidationStep(
                "email_validation",
                List.of(new PathValidatorBinding(FieldPath.of("email"), new EmailFormatValidator())),
                List.of(),
                escalateDef
        );
        ToolPipelineStep rolesLookup = new ToolPipelineStep(
                "roles_lookup", CompanyRolesTool.TOOL_NAME, toolRegistry, Map.of()
        );
        ModelInferenceStep recipientInference = new ModelInferenceStep(
                "recipient_inference", recordingClient, "base-3b", "{}", "System step 2"
        );

        BasePipeline pipeline = new BasePipeline(
                DocumentType.EMAIL,
                List.of(emailInference, emailValidation, rolesLookup, recipientInference)
        );

        PipelineContext context = new PipelineContext("Contattatemi a mario.rossi@example.com", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success());
        assertEquals(3, capturedRequests.size());
        assertEquals("base-3b", capturedRequests.get(0).model());
        assertEquals("escalation-7b", capturedRequests.get(1).model());
        assertEquals(Map.of("temperature", 0.0), capturedRequests.get(1).options());

        // Lo step successivo deve tornare al modello base configurato per recipient_inference e sampling null
        assertEquals("base-3b", capturedRequests.get(2).model(),
                "L'override del modello di escalation non deve propagarsi allo step successivo");
        assertNull(capturedRequests.get(2).options());
    }

    @Test
    @DisplayName("T1.3: Non-regressione su due retry consecutivi dello stesso step (temperature 0.2 -> 0.4)")
    void consecutiveRetriesOnSameStepIncrementTemperature() {
        List<ModelRequest> capturedRequests = new ArrayList<>();
        String[] responses = {
                "{\"mittente\":\"Mario\",\"email\":\"invalida-1\"}",
                "{\"mittente\":\"Mario\",\"email\":\"invalida-2\"}",
                "{\"mittente\":\"Mario\",\"email\":\"mario@example.com\"}"
        };

        ModelClient recordingClient = request -> {
            int idx = capturedRequests.size();
            capturedRequests.add(request);
            return new ModelResponse(responses[idx], 20);
        };

        ModelInferenceStep emailInference = new ModelInferenceStep(
                "email_inference", recordingClient, "base-3b", "{}", "System"
        );
        OutputValidationStep emailValidation = new OutputValidationStep(
                "email_validation",
                List.of(new PathValidatorBinding(FieldPath.of("email"), new EmailFormatValidator())),
                List.of(),
                OnFailureDefinition.fromMap(
                        Map.of("retry_step", "email_inference", "max_retries", 2, "feedback", true)
                )
        );

        BasePipeline pipeline = new BasePipeline(DocumentType.EMAIL, List.of(emailInference, emailValidation));
        PipelineResult result = pipeline.execute(new PipelineContext("Test", DocumentType.EMAIL));

        assertTrue(result.success());
        assertEquals(3, capturedRequests.size());
        assertNull(capturedRequests.get(0).options());
        assertEquals(Map.of("temperature", 0.2), capturedRequests.get(1).options());
        assertEquals(Map.of("temperature", 0.4), capturedRequests.get(2).options());
    }
}
