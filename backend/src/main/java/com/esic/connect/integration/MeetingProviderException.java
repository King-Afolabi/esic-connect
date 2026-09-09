package com.esic.connect.integration;

/**
 * Échec d'un fournisseur de réunion <strong>actif</strong>.
 *
 * <p>Distinct d'une absence d'intégration : ne pas les confondre est ce
 * qui permet de dire « Teams est configuré mais a refusé » plutôt que
 * « pas de lien », deux situations qui n'appellent pas la même action.
 */
public class MeetingProviderException extends RuntimeException {

    public MeetingProviderException(String message) {
        super(message);
    }

    public MeetingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
