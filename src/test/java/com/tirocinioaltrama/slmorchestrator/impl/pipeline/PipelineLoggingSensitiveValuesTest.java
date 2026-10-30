package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.BasePipeline;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldPath;
import com.tirocinioaltrama.slmorchestrator.core.validation.Validator;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep.PathValidatorBinding;

class PipelineLoggingSensitiveValuesTest {

    private TestLogAppender appender;
    private org.apache.logging.log4j.core.Logger log4jLogger;

    static class TestLogAppender extends AbstractAppender {
        private final List<String> messages = new ArrayList<>();

        protected TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        public List<String> getMessages() {
            return messages;
        }

        public void clear() {
            messages.clear();
        }
    }

    @BeforeEach
    void setUp() {
        appender = new TestLogAppender("TestLogAppender");
        appender.start();
        log4jLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(BasePipeline.class);
        log4jLogger.addAppender(appender);
        log4jLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        if (appender != null && log4jLogger != null) {
            appender.stop();
            log4jLogger.removeAppender(appender);
        }
    }

    @Test
    @DisplayName("C1: Con flag false, il valore rifiutato (IBAN) non compare nel log WARN di BasePipeline")
    void testSensitiveValuesExcludedWhenFlagIsFalse() {
        String sensitiveIban = "IT02L1234512345123456789012";
        BasePipeline pipeline = createPipeline(false);

        PipelineContext context = new PipelineContext("test input", DocumentType.FATTURA);
        context.setSanitizedInput("test input");
        context.setModelOutput("{\"iban\":\"" + sensitiveIban + "\"}");

        pipeline.execute(context);

        List<String> logMessages = appender.getMessages();
        boolean containsIban = logMessages.stream().anyMatch(msg -> msg.contains(sensitiveIban));
        assertFalse(containsIban, "Il log non deve contenere il valore sensibile dell'IBAN quando il flag è false. Messaggi registrati: " + logMessages);
        boolean containsWarning = logMessages.stream().anyMatch(msg -> msg.contains("iban: CHECKSUM_INVALID") || msg.contains("iban: FORMAT_INVALID"));
        assertTrue(containsWarning, "Il log deve contenere il path e il codice d'errore. Messaggi registrati: " + logMessages);
    }

    @Test
    @DisplayName("C1: Con flag true, il valore rifiutato (IBAN) compare nel log WARN di BasePipeline")
    void testSensitiveValuesIncludedWhenFlagIsTrue() {
        String sensitiveIban = "IT02L1234512345123456789012";
        BasePipeline pipeline = createPipeline(true);

        PipelineContext context = new PipelineContext("test input", DocumentType.FATTURA);
        context.setSanitizedInput("test input");
        context.setModelOutput("{\"iban\":\"" + sensitiveIban + "\"}");

        pipeline.execute(context);

        List<String> logMessages = appender.getMessages();
        boolean containsIban = logMessages.stream().anyMatch(msg -> msg.contains(sensitiveIban));
        assertTrue(containsIban, "Il log deve contenere il valore sensibile dell'IBAN quando il flag è true. Messaggi registrati: " + logMessages);
    }

    private BasePipeline createPipeline(boolean includeSensitiveValues) {
        ValidatorRegistry reg = new ValidatorRegistry();
        Validator<String> ibanValidator = reg.get("iban").orElseThrow();
        List<PathValidatorBinding> bindings = List.of(new PathValidatorBinding(new FieldPath("iban"), ibanValidator));
        OutputValidationStep ovs = new OutputValidationStep("val", bindings, List.of());

        return new BasePipeline(
                DocumentType.FATTURA,
                List.of(ovs),
                60_000L,
                null,
                includeSensitiveValues
        );
    }
}
