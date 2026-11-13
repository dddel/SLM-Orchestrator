package com.tirocinioaltrama.slmorchestrator.core.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.Router;
import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineConfigurationException;
import com.tirocinioaltrama.slmorchestrator.core.exception.ValidationException;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelMetrics;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineRegistry;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.DefaultOrchestrator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.BasePipeline;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.ModelInferenceStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.OnFailureDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ActionExecutor;

class ContextBudgetValidatorTest {

    @Test
    @DisplayName("T5.1 - Payload entro il budget (1.500 caratteri, num_ctx=2048) -> valido")
    void testPayloadWithinBudgetIsAccepted() {
        ContextBudgetValidator validator = new ContextBudgetValidator();
        String payload = "A".repeat(1500);
        ValidationResult res = validator.validatePayloadBudget(payload, DocumentType.FATTURA);
        assertTrue(res.isValid());
    }

    @Test
    @DisplayName("T5.2 - Payload di 4.000 caratteri con num_ctx=2048 -> rifiutato pre-volo con ValidationException (HTTP 422)")
    void testPayloadExceedingBudgetThrowsValidationException() {
        ContextBudgetValidator budgetValidator = new ContextBudgetValidator();
        Router mockRouter = mock(Router.class);
        PipelineRegistry registry = new PipelineRegistry(List.of(
                new BasePipeline(DocumentType.FATTURA, List.of())
        ));
        com.tirocinioaltrama.slmorchestrator.core.RouterRegistry routerRegistry =
                new com.tirocinioaltrama.slmorchestrator.core.RouterRegistry(
                        Map.of(com.tirocinioaltrama.slmorchestrator.core.RouterType.LOCAL_OLLAMA, mockRouter),
                        com.tirocinioaltrama.slmorchestrator.core.RouterType.LOCAL_OLLAMA
                );
        DefaultOrchestrator orchestrator = new DefaultOrchestrator(
                routerRegistry,
                registry,
                new ActionExecutor(new ToolRegistry()),
                budgetValidator
        );

        String oversizedPayload = "Fattura riga descrittiva molto lunga ".repeat(120); // > 4000 chars
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> orchestrator.process(new OrchestratorRequest(DocumentType.FATTURA, oversizedPayload))
        );
        assertTrue(ex.getMessage().contains("num_ctx"));
        assertFalse(ex.getErrors().isEmpty());
        verifyNoInteractions(mockRouter);
    }

    @Test
    @DisplayName("T5.3 - Configurazione con num_ctx=400 e num_predict=400 -> Fail-Fast al bootstrap (PipelineConfigurationException)")
    void testBootstrapFailFastWhenBudgetIsZeroOrNegative() {
        ContextBudgetValidator invalidValidator = new ContextBudgetValidator(
                400, 400, 2.5, 0, Map.of(DocumentType.FATTURA, 300), 150
        );
        assertThrows(
                PipelineConfigurationException.class,
                invalidValidator::validateConfigurationAtStartup
        );

        // Anche se num_predict < num_ctx ma l'overhead statico supera il margine residuo
        ContextBudgetValidator tightValidator = new ContextBudgetValidator(
                600, 400, 2.5, 0, Map.of(DocumentType.EMAIL, 250), 100
        );
        PipelineConfigurationException ex = assertThrows(
                PipelineConfigurationException.class,
                tightValidator::validateConfigurationAtStartup
        );
        assertTrue(ex.getMessage().contains("EMAIL"));
    }

    @Test
    @DisplayName("T5.4 - Risposta Ollama con prompt_eval_count vicino a num_ctx - num_predict -> CONTEXT_TRUNCATED e INDETERMINATE senza retry")
    void testPostHocContextSaturationShortCircuitsToIndeterminateWithoutRetry() {
        ModelClient mockClient = mock(ModelClient.class);
        // num_ctx=2048, num_predict=400 -> soglia = 2048 - 400 - 16 = 1632. Simuliamo promptTokens = 1640.
        ModelMetrics saturatedMetrics = new ModelMetrics(1640, 120, 500L, 600L, 10L, "stop");
        when(mockClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"fornitore\":{\"nome\":\"Test\"}}", 600L, saturatedMetrics));

        ModelInferenceStep infStep = new ModelInferenceStep("inf", mockClient, "worker-model", "{}", "System prompt");
        OnFailureDefinition onFailure = OnFailureDefinition.fromMap(Map.of(
                "retry_step", "inf",
                "max_retries", 2,
                "feedback", true
        ));
        OutputValidationStep valStep = new OutputValidationStep("val", List.of(), List.of(), onFailure, false, false);
        BasePipeline pipeline = new BasePipeline(DocumentType.FATTURA, List.of(infStep, valStep));

        PipelineContext ctx = new PipelineContext("Testo", DocumentType.FATTURA);
        ctx.setAttribute("context.num_ctx", 2048);
        ctx.setAttribute("context.num_predict", 400);

        PipelineResult result = pipeline.execute(ctx);

        assertFalse(result.success());
        assertEquals(PipelineResult.Outcome.INDETERMINATE, result.outcome());
        assertTrue(result.validationResult().fieldErrors().stream()
                .anyMatch(fe -> fe.code() == ValidationErrorCode.CONTEXT_TRUNCATED));
        // Nessun retry deve essere stato effettuato
        verify(mockClient, times(1)).generate(any(ModelRequest.class));
    }

    @Test
    @DisplayName("T5.5 - Risposta Ollama con done_reason=length -> OUTPUT_TRUNCATED distinto da CONTEXT_TRUNCATED e INDETERMINATE senza retry")
    void testDoneReasonLengthEmitsOutputTruncatedAndShortCircuitsWithoutRetry() {
        ModelClient mockClient = mock(ModelClient.class);
        ModelMetrics lengthMetrics = new ModelMetrics(500, 400, 800L, 900L, 10L, "length");
        when(mockClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"fornitore\":{\"nome\":\"Tronc", 900L, lengthMetrics));

        ModelInferenceStep infStep = new ModelInferenceStep("inf", mockClient, "worker-model", "{}", "System prompt");
        OnFailureDefinition onFailure = OnFailureDefinition.fromMap(Map.of(
                "retry_step", "inf",
                "max_retries", 2,
                "feedback", true
        ));
        OutputValidationStep valStep = new OutputValidationStep("val", List.of(), List.of(), onFailure, false, false);
        BasePipeline pipeline = new BasePipeline(DocumentType.FATTURA, List.of(infStep, valStep));

        PipelineContext ctx = new PipelineContext("Testo", DocumentType.FATTURA);
        PipelineResult result = pipeline.execute(ctx);

        assertFalse(result.success());
        assertEquals(PipelineResult.Outcome.INDETERMINATE, result.outcome());
        assertTrue(result.validationResult().fieldErrors().stream()
                .anyMatch(fe -> fe.code() == ValidationErrorCode.OUTPUT_TRUNCATED));
        assertFalse(result.validationResult().fieldErrors().stream()
                .anyMatch(fe -> fe.code() == ValidationErrorCode.CONTEXT_TRUNCATED));
        verify(mockClient, times(1)).generate(any(ModelRequest.class));
    }

    @Test
    @DisplayName("Bootstrap reale da YamlPipelineLoader: calcolo overhead statico per tutte le pipeline configurate")
    void testSpringConstructorComputesBudgetsFromRealPipelines() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ollama.model", "qwen2.5:3b-instruct-q8_0");
        env.setProperty("ollama.email-model", "qwen2.5:3b-instruct-q8_0");
        env.setProperty("ollama.invoice-model", "qwen2.5:3b-instruct-q8_0");
        env.setProperty("ollama.anagrafica-model", "qwen2.5:3b-instruct-q8_0");
        env.setProperty("ollama.num-ctx", "2048");
        env.setProperty("ollama.num-predict", "400");

        ValidatorRegistry valReg = new ValidatorRegistry();
        ToolRegistry toolReg = new ToolRegistry();
        DefaultResourceLoader resLoader = new DefaultResourceLoader();
        PipelineConfigValidator cfgVal = new PipelineConfigValidator(valReg, toolReg, resLoader);
        YamlPipelineLoader loader = new YamlPipelineLoader(env, valReg, cfgVal, toolReg);

        ContextBudgetValidator budgetValidator = new ContextBudgetValidator(env, loader, resLoader);
        assertDoesNotThrow(budgetValidator::validateConfigurationAtStartup);
        assertTrue(budgetValidator.getBudgetTokensFor(DocumentType.FATTURA) > 400);
        assertTrue(budgetValidator.getBudgetTokensFor(DocumentType.EMAIL) > 400);
    }
}
