package com.esic.connect.notification.internal;

import java.util.Locale;

/**
 * Forme masquée d'une adresse électronique, pour l'affichage du suivi de
 * délivrabilité (EF-USER-008).
 *
 * <p>Objectif : permettre à un responsable de <strong>reconnaître</strong>
 * l'adresse qu'il vient de saisir et de repérer une faute de frappe, sans
 * que la table constitue un annuaire exploitable en cas de fuite
 * (docs/08, minimisation).
 *
 * <p>{@code jean.dupont@esic.fr} devient {@code je…nt@e…c.fr} : assez
 * pour reconnaître, pas assez pour reconstituer.
 */
final class EmailAddressMasking {

    private static final int VISIBLE = 2;

    private EmailAddressMasking() {
    }

    static String mask(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return "(adresse absente)";
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            // Adresse mal formée : on n'en montre rien de plus que sa longueur.
            return "(adresse invalide)";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int lastDot = domain.lastIndexOf('.');
        String domainName = lastDot > 0 ? domain.substring(0, lastDot) : domain;
        String tld = lastDot > 0 ? domain.substring(lastDot) : "";
        return abbreviate(local) + "@" + abbreviate(domainName) + tld;
    }

    private static String abbreviate(String value) {
        if (value.length() <= VISIBLE) {
            return value.charAt(0) + "…";
        }
        return value.charAt(0) + "…" + value.charAt(value.length() - 1);
    }
}
