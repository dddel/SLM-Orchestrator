package com.tirocinioaltrama.slmorchestrator.impl.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.config.email.EmailProperties;
import com.tirocinioaltrama.slmorchestrator.core.email.EmailMessage;
import com.tirocinioaltrama.slmorchestrator.core.email.EmailSender;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;

class SendEmailToolTest {

    @Test
    void sendsPlainTextAcknowledgementToValidatedEmail() {
        AtomicReference<EmailMessage> sentMessage = new AtomicReference<>();
        EmailSender sender = sentMessage::set;
        SendEmailTool tool = new SendEmailTool(sender, properties(true, false));

        ToolResult result = tool.execute(Map.of(
                "document", Map.of(
                        "mittente", "Luca Bianchi",
                        "email", "luca@example.test",
                        "richiesta", "Preventivo workshop"
                )
        ));

        assertTrue(result.success());
        assertEquals("luca@example.test", sentMessage.get().to());
        assertEquals("no-reply@example.test", sentMessage.get().from());
        assertTrue(sentMessage.get().text().contains("Preventivo workshop"));
    }

    @Test
    void skipsSendingWhenDisabled() {
        AtomicReference<EmailMessage> sentMessage = new AtomicReference<>();
        SendEmailTool tool = new SendEmailTool(sentMessage::set, properties(false, false));

        ToolResult result = tool.execute(Map.of(
                "document", Map.of("email", "luca@example.test")
        ));

        assertTrue(result.success());
        assertEquals("Invio email disabilitato", result.message());
        assertFalse(sentMessage.get() != null);
    }

    @Test
    void rejectsRecipientsOutsideConfiguredAllowlist() {
        AtomicReference<EmailMessage> sentMessage = new AtomicReference<>();
        SendEmailTool tool = new SendEmailTool(
                sentMessage::set,
                new EmailProperties(true, false, "no-reply@example.test", List.of("example.test"))
        );

        ToolResult result = tool.execute(Map.of(
                "document", Map.of("email", "luca@external.test")
        ));

        assertFalse(result.success());
        assertTrue(result.message().contains("dominio non consentito"));
        assertFalse(sentMessage.get() != null);
    }

    private EmailProperties properties(boolean enabled, boolean dryRun) {
        return new EmailProperties(enabled, dryRun, "no-reply@example.test", List.of("example.test"));
    }
}
