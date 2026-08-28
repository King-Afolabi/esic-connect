package com.esic.connect.identity.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administration minimale des comptes et des rôles (cahier §6, §9, §29).
 *
 * <p>Le {@code @PreAuthorize} filtre grossièrement par rôle ; les règles
 * fines (protection {@code SUPER_ADMIN}, auto-action, dernier rôle actif,
 * transitions de statut) sont appliquées dans
 * {@link UserManagementService}. Toutes les routes utilisent
 * exclusivement {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/users")
class UserAccountController {

    private static final String READ_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION')";
    private static final String LIFECYCLE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION')";
    private static final String ADMIN_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN')";

    private final UserManagementService userManagementService;

    UserAccountController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    PageResponse<UserSummaryResponse> list(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return userManagementService.listUsers(status, role, q, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(READ_ROLES)
    UserDetailResponse get(@PathVariable String publicId) {
        return userManagementService.getUser(parseUuid(publicId));
    }

    @PostMapping("/{publicId}/suspend")
    @PreAuthorize(LIFECYCLE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void suspend(@PathVariable String publicId,
                 @Valid @RequestBody AccountActionRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        userManagementService.suspend(parseUuid(publicId), request.reason().trim(),
                subject(caller), roles(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(LIFECYCLE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId,
                 @Valid @RequestBody AccountActionRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        userManagementService.restore(parseUuid(publicId), request.reason().trim(),
                subject(caller), roles(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(ADMIN_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody AccountActionRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        userManagementService.archive(parseUuid(publicId), request.reason().trim(),
                subject(caller), roles(caller));
    }

    @PostMapping("/{publicId}/roles")
    @PreAuthorize(ADMIN_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void assignRole(@PathVariable String publicId,
                    @Valid @RequestBody AssignRoleRequest request,
                    @AuthenticationPrincipal Jwt caller) {
        userManagementService.assignRole(parseUuid(publicId), request.role(), request.reason().trim(),
                subject(caller), roles(caller));
    }

    @PostMapping("/{publicId}/roles/{roleCode}/revoke")
    @PreAuthorize(ADMIN_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeRole(@PathVariable String publicId,
                    @PathVariable String roleCode,
                    @Valid @RequestBody AccountActionRequest request,
                    @AuthenticationPrincipal Jwt caller) {
        userManagementService.revokeRole(parseUuid(publicId), roleCode, request.reason().trim(),
                subject(caller), roles(caller));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            // Un identifiant mal formé ne désigne aucun compte.
            throw new UserManagementException(UserManagementException.Kind.USER_NOT_FOUND);
        }
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }

    private static List<String> roles(Jwt caller) {
        if (caller == null) {
            return List.of();
        }
        List<String> claim = caller.getClaimAsStringList("roles");
        return claim != null ? claim : List.of();
    }
}
