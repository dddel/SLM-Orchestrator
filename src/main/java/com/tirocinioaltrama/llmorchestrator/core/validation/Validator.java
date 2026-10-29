package com.tirocinioaltrama.llmorchestrator.core.validation;

/**
 * Validatore per un tipo generico di dato o payload.
 *
 * @param <T> il tipo di dato da validare
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * Esegue la validazione dell'input fornito.
     *
     * @param target l'oggetto o payload da validare
     * @return il risultato immutabile della validazione
     */
    ValidationResult validate(T target);
}
