package com.tirocinioaltrama.llmorchestrator.core.validation;

/**
 * Codici errore standardizzati per la validazione deterministica e sintattica.
 */
public enum ValidationErrorCode {
    JSON_SYNTAX_INVALID,
    REQUIRED_MISSING,
    REQUIRED_EMPTY,
    REQUIRED_NULL,
    FORMAT_INVALID,
    LENGTH_INVALID,
    CHECKSUM_INVALID,
    SOURCE_CHECKSUM_INVALID,
    VALUE_EMPTY,
    FIELD_SWAP_DETECTED,
    INFERENCE_ERROR
}
