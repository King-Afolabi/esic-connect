package com.esic.connect.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestion commune minimale des erreurs de l'API.
 *
 * Ce socle ne couvre que les cas génériques (validation, erreur inattendue).
 * Les codes d'erreur métier spécifiques seront ajoutés module par module.
 *
 * L'identifiant de corrélation est généré ici faute d'un filtre de
 * corrélation dédié pour l'instant (voir docs/03-architecture.md §27.3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La requête contient des champs invalides.",
                request, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Une erreur inattendue est survenue.",
                request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                            HttpServletRequest request, List<String> details) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                UUID.randomUUID().toString(),
                details);
        return ResponseEntity.status(status).body(body);
    }
}
