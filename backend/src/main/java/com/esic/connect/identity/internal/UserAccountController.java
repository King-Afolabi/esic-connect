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
    private final AccountInvitationService invitationService;
    private final BulkUserService bulkUserService;
    private final StepUpGuard stepUpGuard;

    UserAccountController(UserManagementService userManagementService,
                          AccountInvitationService invitationService,
                          BulkUserService bulkUserService,
                          StepUpGuard stepUpGuard) {
        this.userManagementService = userManagementService;
        this.invitationService = invitationService;
        this.bulkUserService = bulkUserService;
        this.stepUpGuard = stepUpGuard;
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

    /**
     * Crée un compte en attente d'activation et, sauf demande contraire,
     * lui émet immédiatement son invitation (EF-USER-001, EF-USER-007).
     *
     * <p>La création et l'invitation sont deux transactions distinctes et
     * assumées comme telles : le compte existe même si le courriel
     * échoue, et le journal de délivrabilité (EF-USER-008) montre alors
     * qu'il faut corriger l'adresse et réémettre. L'inverse — perdre le
     * compte parce que le serveur SMTP est tombé — serait pire.
     */
    @PostMapping
    @PreAuthorize(ADMIN_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    UserDetailResponse create(@Valid @RequestBody CreateUserRequest request,
                              @AuthenticationPrincipal Jwt caller) {
        UserDetailResponse created = userManagementService.createUser(request,
                subject(caller), roles(caller));
        if (request.shouldSendInvitation()) {
            invitationService.issue(request.email(), RoleCode.valueOf(request.role().toUpperCase(
                    java.util.Locale.ROOT)), subject(caller));
        }
        return created;
    }

    /**
     * Opération groupée (EF-USER-004 ; docs/02 §9.4 et §30.2 :
     * {@code POST /users/bulk}).
     *
     * <p>Sans {@code confirm: true}, l'appel <strong>prévisualise</strong> :
     * rien n'est écrit, et la réponse chiffre les comptes éligibles,
     * ignorés et refusés. C'est le cahier qui l'exige (RG-034), et c'est
     * la seule protection contre un clic qui suspendrait cinq cents
     * comptes.
     */
    @PostMapping("/bulk")
    @PreAuthorize(LIFECYCLE_ROLES)
    BulkUserWeb.BulkResult bulk(@Valid @RequestBody BulkUserWeb.BulkRequest request,
                                @AuthenticationPrincipal Jwt caller) {
        return bulkUserService.execute(request, subject(caller), roles(caller));
    }

    /**
     * Comptes soupçonnés d'être des doublons (EF-USER-005).
     *
     * <p>Le service <em>signale</em>, il ne fusionne ni ne supprime : une
     * suppression de doublon reste une action humaine, exceptionnelle et
     * doublement confirmée (docs/02 §9.5).
     */
    @GetMapping("/duplicates")
    @PreAuthorize(ADMIN_ROLES)
    java.util.List<BulkUserWeb.DuplicateGroup> duplicates() {
        return bulkUserService.findDuplicates();
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
        // Modifier les droits d'autrui est une action critique : le jeton
        // doit attester d'un facteur fort, pas seulement d'un mot de passe
        // (EF-AUTH-015, RG-009).
        stepUpGuard.requireStrongAuthentication(caller);
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
        stepUpGuard.requireStrongAuthentication(caller);
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
