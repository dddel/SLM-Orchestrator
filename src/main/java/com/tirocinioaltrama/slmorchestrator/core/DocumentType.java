package com.tirocinioaltrama.slmorchestrator.core;

/**
 * Tipologie di documento / etichette supportate dall'orchestratore per lo smistamento
 * verso worker e schemi specializzati.
 */
public enum DocumentType {
    EMAIL,
    FATTURA,
    ANAGRAFICA,
    ALTRO
}
