package com.tirocinioaltrama.slmorchestrator.impl.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.tirocinioaltrama.slmorchestrator.impl.tool.ActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorResponse;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorStatus;
import com.tirocinioaltrama.slmorchestrator.core.Router;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.DefaultOrchestrator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.ActionDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.ActionsGroupDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.StepDefinition.FieldValidatorDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

/**
 * Suite di test per RF8: Layer Azioni Tool post-pipeline con modello actions.
 */
class ActionExecutorTest {

    private ModelClient modelClient;
    private Router router;
    private ValidatorRegistry validatorRegistry;
    private ToolRegistry toolRegistry;
    private PipelineConfigValidator configValidator;
    private MockEnvironment env;

    @BeforeEach
    void setUp() {
        modelClient = mock(ModelClient.class);
        router = mock(Router.class);
        validatorRegistry = new ValidatorRegistry();
        toolRegistry = new ToolRegistry();
        configValidator = new PipelineConfigValidator(validatorRegistry, toolRegistry, new DefaultResourceLoader());
        env = new MockEnvironment();
    }

    private DefaultOrchestrator createOrchestrator(PipelineDefinition def) {
        YamlPipelineLoader loader = new YamlPipelineLoader(env, validatorRegistry, configValidator) {
            @Override
            public List<PipelineDefinition> loadDefinitions() {
                return List.of(def);
            }
        };
        PipelineRegistry registry = new PipelineRegistry(loader.loadPipelines(modelClient));
        ActionExecutor executor = new ActionExecutor(toolRegistry);
        return new DefaultOrchestrator(router, registry, executor);
    }

    @Test
    @DisplayName("RF8: Esecuzione azione on_ok (save_to_db) con successo e risoluzione $result tipizzata")
    void testActionOnOkSuccess() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        toolRegistry.register(new Tool() {
            @Override
            public String getName() {
                return "custom_save";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                toolExecuted.set(true);
                assertNotNull(args.get("doc"));
                return ToolResult.ok("Salvato");
            }
        });

        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("model-test");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));

        ActionsGroupDefinition actions = new ActionsGroupDefinition();
        actions.setOnOk(List.of(new ActionDefinition("custom_save", Map.of("doc", "$result"))));
        def.setActions(actions);
        def.setSteps(List.of(inf, val));

        DefaultOrchestrator orchestrator = createOrchestrator(def);

        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"mittente\":\"Mario\",\"email\":\"mario@test.it\"}", 40));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Test"));

        assertEquals(OrchestratorStatus.OK, resp.status());
        assertTrue(toolExecuted.get());
        assertTrue(resp.actionErrors().isEmpty());
    }

    @Test
    @DisplayName("RF8: Fallimento azione on_ok mantiene status OK e popola actionErrors")
    void testActionOnOkFailurePreservesOkStatus() {
        toolRegistry.register(new Tool() {
            @Override
            public String getName() {
                return "failing_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.failure("DB connection timeout");
            }
        });

        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("model-test");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));

        ActionsGroupDefinition actions = new ActionsGroupDefinition();
        actions.setOnOk(List.of(new ActionDefinition("failing_tool", Map.of("doc", "$result"))));
        def.setActions(actions);
        def.setSteps(List.of(inf, val));

        DefaultOrchestrator orchestrator = createOrchestrator(def);

        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"mittente\":\"Mario\",\"email\":\"mario@test.it\"}", 40));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Test"));

        assertEquals(OrchestratorStatus.OK, resp.status());
        assertNotNull(resp.result());
        assertFalse(resp.actionErrors().isEmpty());
        assertTrue(resp.actionErrors().get(0).contains("DB connection timeout"));
    }

    @Test
    @DisplayName("RF8: Esecuzione azione on_indeterminate (contact_operator) con $errors e $unvalidatedResult")
    void testActionOnIndeterminate() {
        AtomicBoolean contactCalled = new AtomicBoolean(false);
        toolRegistry.register(new Tool() {
            @Override
            public String getName() {
                return "contact_operator";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                contactCalled.set(true);
                assertNotNull(args.get("motivo"));
                assertNotNull(args.get("bozza"));
                return ToolResult.ok("Operatore notificato");
            }
        });

        PipelineDefinition def = new PipelineDefinition();
        def.setId("test_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("model-test");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));

        ActionsGroupDefinition actions = new ActionsGroupDefinition();
        actions.setOnIndeterminate(List.of(new ActionDefinition("contact_operator", Map.of(
                "motivo", "$errors",
                "bozza", "$unvalidatedResult"
        ))));
        def.setActions(actions);
        def.setSteps(List.of(inf, val));

        DefaultOrchestrator orchestrator = createOrchestrator(def);

        // Risposta non valida
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"mittente\":\"Mario\",\"email\":\"non_valida\"}", 40));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Test"));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertTrue(contactCalled.get());
        assertTrue(resp.actionErrors().isEmpty());
    }
}
