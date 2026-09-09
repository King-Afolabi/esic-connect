package com.esic.connect.identity.internal;

import org.springframework.http.HttpStatus;

/**
 * Refus métier du parcours de second facteur.
 *
 * <p>Les libellés sont volontairement <strong>non discriminants</strong> :
 * un code invalide, un défi expiré et un compte inconnu produisent des
 * messages qui ne permettent pas de distinguer les trois cas depuis
 * l'extérieur.
 */
public class MfaException extends RuntimeException {

    public enum Code {
        CHALLENGE_NOT_FOUND(HttpStatus.UNAUTHORIZED,
                "Cette étape de vérification n'est plus valide. Reprenez la connexion."),
        INVALID_CODE(HttpStatus.UNAUTHORIZED,
                "Code incorrect ou expiré."),
        CODE_ALREADY_USED(HttpStatus.UNAUTHORIZED,
                "Ce code a déjà été utilisé. Attendez le suivant."),
        NO_PENDING_ENROLLMENT(HttpStatus.CONFLICT,
                "Aucun enrôlement en cours. Recommencez l'ajout du second facteur."),
        ALREADY_ENROLLED(HttpStatus.CONFLICT,
                "Un second facteur est déjà actif sur ce compte."),
        NOT_ENROLLED(HttpStatus.CONFLICT,
                "Aucun second facteur n'est actif sur ce compte."),
        REQUIRED_BY_ROLE(HttpStatus.CONFLICT,
                "Le second facteur est obligatoire pour vos rôles : il ne peut pas être retiré.");

        private final HttpStatus status;
        private final String message;

        Code(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public HttpStatus status() {
            return status;
        }

        public String message() {
            return message;
        }
    }

    private final Code code;

    public MfaException(Code code) {
        super(code.message());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
