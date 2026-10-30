package com.tirocinioaltrama.slmorchestrator.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tirocinioaltrama.slmorchestrator.core.Orchestrator;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST pubblico dell'orchestratore.
 * Espone solo l'interfaccia verso {@link Orchestrator}.
 * Cattura esclusivamente il payload dell'utente senza esporre alcun dettaglio
 * sui modelli o schemi interni.
 */
@Tag(name = "Orchestratore SLM", description = "Endpoint principale per l'instradamento, l'estrazione e la validazione neuro-simbolica dei documenti")
@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorController.class);
    private final Orchestrator orchestrator;

    public OrchestratorController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Operation(
            summary = "Elabora un documento testuale non strutturato",
            description = "Esegue routing intelligente, estrazione vincolata su JSON Schema con SLM locali (Qwen 2.5), validazione formale deterministica (CF, P.IVA, IBAN, Email), retry ed escalation automatica.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Elaborazione completata (Stato OK o INDETERMINATE) con metriche e token"),
                    @ApiResponse(responseCode = "422", description = "Errore di validazione dell'input o etichetta non supportata"),
                    @ApiResponse(responseCode = "502", description = "Errore di connessione o risposta dai motori di inferenza")
            }
    )
    @PostMapping("/process")
    public ResponseEntity<OrchestratorResponse> process(@RequestBody OrchestratorRequest request) {
        log.info("Ricevuta chiamata REST POST /api/orchestrator/process");
        OrchestratorResponse response = orchestrator.process(request);
        return ResponseEntity.ok(response);
    }
}
 