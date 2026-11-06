package com.tirocinioaltrama.slmorchestrator.core.validation;

import java.util.Set;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;

/**
 * Valida che l'etichetta restituita dal Router corrisponda ad un {@link DocumentType}
 * supportato e registrato nel sistema.
 */
public class LabelValidator implements Validator<DocumentType> {

    private final Set<DocumentType> supportedTypes;

    public LabelValidator(Set<DocumentType> supportedTypes) {
        this.supportedTypes = supportedTypes != null ? Set.copyOf(supportedTypes) : Set.of();
    }

    @Override
    public ValidationResult validate(DocumentType type) {
        if (type == null) {
            return ValidationResult.error("L'etichetta classificata dal Router non può essere nulla.");
        }

        if (!supportedTypes.contains(type)) {
            return ValidationResult.error(
                    "L'etichetta '" + type + "' non corrisponde ad alcuna pipeline attiva nel sistema. Pipeline registrate: " + supportedTypes
            );
        }

        return ValidationResult.valid();
    }
}
