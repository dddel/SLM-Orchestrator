package com.tirocinioaltrama.llmorchestrator.core.validation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.tirocinioaltrama.llmorchestrator.core.validation.field.CodiceFiscaleValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.EmailFormatValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.IbanValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.PartitaIvaValidator;

/**
 * Registro centrale dei validatori deterministici disponibili per nome identificativo.
 * Consente l'aggancio dinamico dei validatori nelle definizioni YAML delle pipeline.
 */
@Component
public class ValidatorRegistry {

    private final Map<String, Validator<String>> validators = new LinkedHashMap<>();

    public ValidatorRegistry() {
        registerDefaultValidators();
    }

    private void registerDefaultValidators() {
        register("email", new EmailFormatValidator());
        register("codice_fiscale", new CodiceFiscaleValidator());
        register("partita_iva", new PartitaIvaValidator());
        register("iban", new IbanValidator());
        register("codice_fiscale_o_partita_iva", new com.tirocinioaltrama.llmorchestrator.core.validation.field.CodiceFiscaleOPartitaIvaValidator());
    }

    public synchronized void register(String name, Validator<String> validator) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Il nome del validatore non può essere nullo o vuoto.");
        }
        if (validator == null) {
            throw new IllegalArgumentException("L'istanza del validatore non può essere nulla.");
        }
        validators.put(name.strip().toLowerCase(), validator);
    }

    public Optional<Validator<String>> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(validators.get(name.strip().toLowerCase()));
    }

    public boolean contains(String name) {
        if (name == null) {
            return false;
        }
        return validators.containsKey(name.strip().toLowerCase());
    }

    public Collection<String> getAvailableValidatorNames() {
        return Collections.unmodifiableSet(validators.keySet());
    }
}
