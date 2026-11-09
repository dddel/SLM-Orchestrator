package com.tirocinioaltrama.slmorchestrator.impl.tool;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.tirocinioaltrama.slmorchestrator.config.email.EmailProperties;
import com.tirocinioaltrama.slmorchestrator.core.email.EmailMessage;
import com.tirocinioaltrama.slmorchestrator.core.email.EmailSender;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;

/**
 * Azione post-pipeline per inviare un riepilogo testuale dell'output validato.
 */
@Component
public class SendEmailTool implements Tool {

    public static final String TOOL_NAME = "send_email";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final EmailSender emailSender;
    private final EmailProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SendEmailTool(EmailSender emailSender, EmailProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
    }

    public static SendEmailTool disabled() {
        return new SendEmailTool(message -> {
        }, new EmailProperties(false, true, "no-reply@example.test", java.util.List.of()));
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (!properties.enabled()) {
            return ToolResult.ok("Invio email disabilitato");
        }
        if (properties.dryRun()) {
            return ToolResult.ok("Invio email in dry-run");
        }

        Object document = args != null ? args.get("document") : null;
        String recipient = readValue(document, "email");
        if (recipient == null || !EMAIL_PATTERN.matcher(recipient).matches()) {
            return ToolResult.failure("Destinatario email assente o non valido");
        }
        if (!isAllowedRecipient(recipient)) {
            return ToolResult.failure("Destinatario con dominio non consentito");
        }

        String subject = "Elaborazione email completata";
        String text = "L'elaborazione della tua email è stata completata.\n\n"
                + "Dati estratti:\n"
                + renderDocument(document);
        emailSender.send(new EmailMessage(properties.from(), recipient, subject, text));
        return ToolResult.ok("Email inviata a " + recipient);
    }

    private boolean isAllowedRecipient(String recipient) {
        if (properties.allowedRecipientDomains().isEmpty()) {
            return true;
        }
        String domain = recipient.substring(recipient.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return properties.allowedRecipientDomains().stream()
                .anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
    }

    private String readValue(Object document, String field) {
        if (document instanceof Map<?, ?> map) {
            Object value = map.get(field);
            return value != null ? value.toString().strip() : null;
        }
        if (document instanceof JsonNode node && node.hasNonNull(field)) {
            return node.get(field).asText().strip();
        }
        return null;
    }

    private String renderDocument(Object document) {
        if (document == null) {
            return "{}";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (Exception e) {
            return String.valueOf(document);
        }
    }
}
