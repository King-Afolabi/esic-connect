package com.esic.connect.identity.internal;

import org.springframework.http.HttpStatus;

/**
 * Refus d'un parcours passkey.
 *
 * <p>Les messages ne distinguent jamais « cette passkey est inconnue » de
 * « cette signature est fausse » : la différence renseignerait sur les
 * comptes existants.
 */
public class WebAuthnException extends RuntimeException {

    public enum Code {
        CHALLENGE_NOT_FOUND(HttpStatus.UNAUTHORIZED,
                "Cette demande n'est plus valide. Recommencez."),
        VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED,
                "La clé d'accès n'a pas pu être vérifiée."),
        CREDENTIAL_NOT_FOUND(HttpStatus.NOT_FOUND,
                "Clé d'accès introuvable."),
        ACCOUNT_NOT_ELIGIBLE(HttpStatus.CONFLICT,
                "Ce compte ne permet pas cette opération.");

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

    public WebAuthnException(Code code) {
        super(code.message());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
