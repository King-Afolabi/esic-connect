package com.esic.connect.identity;

/**
 * Levée par {@link StudentAccountProvisioner#prepareStudentAccountAndInvitation}
 * lorsqu'un compte correspondant à l'e-mail existe mais n'est pas
 * {@code PENDING_ACTIVATION} (rapport §4.1). Aucune écriture n'a eu lieu ;
 * l'orchestrateur d'import la retraduit en anomalie {@code IMP_*} et
 * abandonne la confirmation (rollback complet). Aucune donnée personnelle
 * dans le message.
 */
public class StudentAccountProvisioningException extends RuntimeException {

    /** Raison de l'échec — exhaustif pour cette tranche. */
    public enum Reason {
        /** Le compte existe et n'est ni {@code PENDING_ACTIVATION} : rien à préparer. */
        ACCOUNT_NOT_USABLE
    }

    private final Reason reason;

    public StudentAccountProvisioningException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
