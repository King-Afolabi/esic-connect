package com.esic.connect.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La requête contient des champs invalides.",
                request, details);
    }

    /**
     * Réponse strictement uniforme quel que soit le motif réel
     * (email inconnu, mot de passe incorrect, compte non actif) : le
     * détail n'est jamais exposé à l'appelant, seulement à l'audit
     * interne (docs/02 §27.2, §49).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "Adresse électronique ou mot de passe incorrect.", request, List.of());
    }

    /**
     * Refus d'autorisation ({@code @PreAuthorize}, contrôle de périmètre).
     * Sans ce handler, le catch-all générique renverrait un 500 : la
     * réponse doit être un 403 neutre (docs/07-securite-rgpd.md §7,
     * docs/02 §29.2).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accès refusé.", request, List.of());
    }

    /**
     * Sans ce handler explicite, le catch-all générique ci-dessous
     * masquerait le 404 standard de Spring (aucune route ni ressource
     * statique) derrière un 500 trompeur.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Ressource introuvable.", request, List.of());
    }

    /**
     * Paramètre de requête obligatoire absent.
     *
     * <p>Sans ce handler, le catch-all générique transformait une erreur
     * d'appel du client en {@code 500 INTERNAL_ERROR} : c'est le défaut
     * F-SEC-1 relevé sur {@code GET /api/v1/planning/versions} sans
     * {@code classGroupPublicId} (docs/reports/DEMO_CRITICAL_PATH_DIAGNOSTIC.md
     * §2, reconfirmé par l'audit QA du 3 septembre 2026, audit-report.md
     * §3). Un 500 signale à tort une panne serveur, fausse la supervision
     * et est trompeur pour tout client de l'API documentée (OpenAPI).
     *
     * <p>Seul le <strong>nom</strong> du paramètre est renvoyé : il fait
     * partie du contrat public de l'API, aucune information
     * d'implémentation n'est exposée.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La requête contient des champs invalides.",
                request, List.of(ex.getParameterName() + ": paramètre obligatoire absent"));
    }

    /** Partie multipart obligatoire absente (même raisonnement que ci-dessus). */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La requête contient des champs invalides.",
                request, List.of(ex.getRequestPartName() + ": partie obligatoire absente"));
    }

    /** Paramètre présent mais de type incompatible ({@code ?page=abc}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La requête contient des champs invalides.",
                request, List.of(ex.getName() + ": valeur invalide"));
    }

    /**
     * Corps de requête absent ou mal formé (JSON invalide). Erreur du
     * client, jamais une panne serveur : le message d'analyse reste côté
     * serveur.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Le corps de la requête est absent ou mal formé.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Le détail reste côté serveur uniquement : la réponse au client
        // ne doit jamais exposer de trace ni de message d'implémentation.
        log.error("Erreur inattendue sur {}", request.getRequestURI(), ex);
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
