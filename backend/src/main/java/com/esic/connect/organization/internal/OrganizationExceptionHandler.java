package com.esic.connect.organization.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduit {@link OrganizationException} en réponse {@link ApiError}
 * homogène. Aucun message ne divulgue de donnée personnelle.
 */
@RestControllerAdvice(assignableTypes = {
        SiteController.class,
        BuildingController.class,
        RoomController.class,
        SiteNetworkRangeController.class
})
class OrganizationExceptionHandler {

    @ExceptionHandler(OrganizationException.class)
    ResponseEntity<ApiError> handle(OrganizationException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case SITE_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "SITE_NOT_FOUND";
                message = "Aucun site ne correspond à cet identifiant.";
            }
            case BUILDING_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "BUILDING_NOT_FOUND";
                message = "Aucun bâtiment ne correspond à cet identifiant.";
            }
            case ROOM_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ROOM_NOT_FOUND";
                message = "Aucune salle ne correspond à cet identifiant.";
            }
            case NETWORK_RANGE_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "NETWORK_RANGE_NOT_FOUND";
                message = "Aucune plage réseau ne correspond à cet identifiant.";
            }
            case DUPLICATE_CODE -> {
                status = HttpStatus.CONFLICT;
                code = "ORG_DUPLICATE_CODE";
                message = "Ce code est déjà utilisé dans ce périmètre.";
            }
            case DUPLICATE_ACTIVE_RANGE -> {
                status = HttpStatus.CONFLICT;
                code = "ORG_DUPLICATE_ACTIVE_RANGE";
                message = "Une plage réseau active identique existe déjà pour ce site.";
            }
            case ENTITY_ARCHIVED -> {
                status = HttpStatus.CONFLICT;
                code = "ORG_ENTITY_ARCHIVED";
                message = "Cette entité est archivée : restaurez-la avant de la modifier.";
            }
            case INVALID_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "ORG_INVALID_STATE";
                message = "L'état actuel ne permet pas cette opération.";
            }
            case HAS_ACTIVE_CHILDREN -> {
                status = HttpStatus.CONFLICT;
                code = "ORG_HAS_ACTIVE_CHILDREN";
                message = "Archivez d'abord les éléments actifs rattachés.";
            }
            case ARCHIVED_PARENT -> {
                status = HttpStatus.CONFLICT;
                code = "ORG_ARCHIVED_PARENT";
                message = "L'élément parent est archivé.";
            }
            case INVALID_TIME_ZONE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ORG_INVALID_TIME_ZONE";
                message = "Fuseau horaire inconnu (identifiant IANA attendu, ex. Europe/Paris).";
            }
            case INVALID_COUNTRY_CODE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ORG_INVALID_COUNTRY_CODE";
                message = "Code pays invalide (ISO 3166-1 alpha-2 attendu, ex. FR).";
            }
            case INVALID_CIDR -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ORG_INVALID_CIDR";
                message = "Notation CIDR IPv4 ou IPv6 invalide.";
            }
            case BUILDING_SITE_MISMATCH -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ORG_BUILDING_SITE_MISMATCH";
                message = "Le bâtiment indiqué n'appartient pas au site de la salle.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ORG_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ORG_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
