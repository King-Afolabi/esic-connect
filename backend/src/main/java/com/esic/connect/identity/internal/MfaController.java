package com.esic.connect.identity.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Second facteur TOTP : enrôlement, vérification, codes de récupération
 * (EF-AUTH-008, EF-AUTH-009 ; docs/02 §17.3 et §30.2).
 *
 * <p><strong>Deux acteurs possibles</strong> sur les trois premières
 * routes, et un seul point où cela se décide — {@link #resolveActor} :
 * <ul>
 *   <li>une session déjà ouverte : le sujet du jeton porteur fait foi ;</li>
 *   <li>une connexion suspendue par la politique : l'identifiant de défi
 *       fait foi, et lui seul — il a été délivré après vérification du
 *       mot de passe, il vaut donc preuve de la première étape.</li>
 * </ul>
 * Un appel sans jeton <em>et</em> sans défi valide est refusé. Un défi
 * n'est jamais accepté pour un autre compte que celui pour lequel il a
 * été ouvert.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    /**
     * Démarre un enrôlement et renvoie le secret à recopier. Le secret
     * n'est renvoyé qu'ici : un appel ultérieur ne le redonne pas.
     */
    @PostMapping("/enroll")
    public MfaWeb.EnrollmentResponse enroll(@RequestBody(required = false) MfaWeb.EnrollRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        String challengeId = request == null ? null : request.challengeId();
        UUID actor = resolveActor(jwt, challengeId, MfaChallengePurpose.ENROLL);
        return mfaService.startEnrollment(actor);
    }

    /**
     * Confirme l'enrôlement. Lorsque l'appel vient d'une connexion
     * suspendue, la réponse porte aussi le jeton d'accès : l'utilisateur
     * n'a pas à ressaisir son mot de passe après avoir enrôlé.
     */
    @PostMapping("/enroll/confirm")
    public MfaWeb.ConfirmEnrollmentResponse confirmEnrollment(
            @Valid @RequestBody MfaWeb.ConfirmEnrollmentRequest request,
            @RequestHeader(value = AuthController.DEVICE_HEADER, required = false) String deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        String challengeId = request.challengeId();
        UUID actor = resolveActor(jwt, challengeId, MfaChallengePurpose.ENROLL);
        MfaWeb.ConfirmationResult result = mfaService.confirmEnrollment(actor, request.code());

        if (challengeId != null && !challengeId.isBlank() && jwt == null) {
            mfaService.closeChallenge(challengeId);
            mfaService.rememberDevice(actor, deviceId);
            LoginResponse session = mfaService.issueFor(mfaService.requireAccount(actor),
                    List.of(AccessTokenIssuer.AMR_PASSWORD, AccessTokenIssuer.AMR_OTP));
            return new MfaWeb.ConfirmEnrollmentResponse(result.recoveryCodes(), session);
        }
        return new MfaWeb.ConfirmEnrollmentResponse(result.recoveryCodes(), null);
    }

    /**
     * Résout un défi de connexion : code TOTP courant ou code de
     * récupération à usage unique. Route publique par nécessité — aucun
     * jeton n'a encore été délivré — mais inexploitable sans un défi
     * valide, lui-même délivré contre un mot de passe correct.
     */
    @PostMapping("/verify")
    public LoginResponse verify(@Valid @RequestBody MfaWeb.VerifyRequest request,
                                @RequestHeader(value = AuthController.DEVICE_HEADER,
                                        required = false) String deviceId) {
        return mfaService.verifyChallenge(request.challengeId(), request.code(), deviceId);
    }

    /** État du second facteur du compte connecté. */
    @GetMapping
    public MfaWeb.StatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return mfaService.status(UUID.fromString(jwt.getSubject()));
    }

    /**
     * Retire le second facteur, sur preuve d'un code valide. Refusé si un
     * rôle détenu l'impose (RG-007).
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Valid @RequestBody MfaWeb.CodeRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        mfaService.disable(UUID.fromString(jwt.getSubject()), request.code());
    }

    /** Renouvelle la série de codes de récupération ; les anciens sont invalidés. */
    @PostMapping("/recovery-codes")
    public MfaWeb.ConfirmationResult regenerateRecoveryCodes(@Valid @RequestBody MfaWeb.CodeRequest request,
                                                            @AuthenticationPrincipal Jwt jwt) {
        return new MfaWeb.ConfirmationResult(
                mfaService.regenerateRecoveryCodes(UUID.fromString(jwt.getSubject()), request.code()));
    }

    /**
     * Identité de l'acteur : le jeton s'il y en a un, sinon le défi.
     *
     * <p>Le jeton l'emporte volontairement sur le défi : un appelant
     * authentifié ne peut pas, en glissant un identifiant de défi obtenu
     * pour un autre compte, agir sur ce compte.
     */
    private UUID resolveActor(Jwt jwt, String challengeId, MfaChallengePurpose purpose) {
        if (jwt != null) {
            return UUID.fromString(jwt.getSubject());
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new MfaException(MfaException.Code.CHALLENGE_NOT_FOUND);
        }
        return mfaService.requireChallenge(challengeId, purpose).userPublicId();
    }
}
