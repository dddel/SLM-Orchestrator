package com.tirocinioaltrama.slmorchestrator.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineExecutionException;
import com.tirocinioaltrama.slmorchestrator.core.exception.RoutingException;
import com.tirocinioaltrama.slmorchestrator.core.exception.ValidationException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ControllerAdvice globale che centralizza la gestione delle eccezioni applicative e di framework,
 * convertendole in risposte HTTP deterministiche e strutturate in formato {@link ApiErrorResponse}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Gestione errori di validazione di business/dominio (es. input nullo, formato campo errato).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request
    ) {
        log.warn("Errore di validazione intercettato su '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrors()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Gestione di argomenti non validi generici (es. IllegalArgumentException).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        log.warn("Richiesta non valida intercettata su '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Gestione di payload HTTP malformati o JSON client non parsabili.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("Body della richiesta HTTP non leggibile su '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Il corpo della richiesta HTTP è malformato o assente.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Gestione errori durante la fase di classificazione / routing.
     */
    @ExceptionHandler(RoutingException.class)
    public ResponseEntity<ApiErrorResponse> handleRoutingException(
            RoutingException ex,
            HttpServletRequest request
    ) {
        log.error("Errore durante il routing su '{}': {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * Gestione errori durante l'esecuzione della pipeline o del worker SLM locale.
     */
    @ExceptionHandler(PipelineExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handlePipelineExecutionException(
            PipelineExecutionException ex,
            HttpServletRequest request
    ) {
        log.error("Errore durante l'esecuzione della pipeline su '{}': {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Fallback per qualsiasi eccezione inattesa del server.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Eccezione non gestita intercettata su '{}': {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Si è verificato un errore interno durante l'elaborazione della richiesta.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
