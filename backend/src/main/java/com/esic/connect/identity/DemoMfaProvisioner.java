package com.esic.connect.identity;

/**
 * Port public d'amorçage <strong>réservé au profil {@code demo}</strong>.
 *
 * <p>Permet à un initialiseur de démonstration d'activer, de façon
 * idempotente, un second facteur TOTP <em>déterministe</em> pour un compte
 * fictif déjà créé par {@link DemoAccountProvisioner} — en contournant le
 * parcours d'enrôlement HTTP en deux temps ({@code /mfa/enroll} puis
 * {@code /mfa/enroll/confirm}), inutilisable ici puisqu'il n'existe encore
 * aucune session à amorcer. L'implémentation n'est enregistrée que sous le
 * profil {@code demo} : ce n'est pas une porte dérobée en production, et le
 * secret n'est jamais accepté que via une valeur explicitement fournie par
 * l'appelant (jamais une valeur par défaut codée en dur).
 *
 * <p>Sans ce mécanisme, un compte {@code ADMIN} ou {@code SUPER_ADMIN} de
 * démonstration reste bloqué à l'écran d'enrôlement à chaque connexion (le
 * secret généré aléatoirement par le parcours normal n'est jamais connu à
 * l'avance d'un script ou d'une suite de bout en bout), ce qui rend
 * impossible toute automatisation de {@code scripts/seed-demo.sh} ou de la
 * recette navigateur sur ces deux rôles (dette T-19/T-20,
 * {@code docs/CURRENT-STATE.md}).
 */
public interface DemoMfaProvisioner {

    /**
     * Garantit qu'un facteur TOTP {@code ACTIVE} existe pour {@code email},
     * construit à partir de {@code base32Secret}.
     *
     * <p>Idempotent : si un facteur actif existe déjà avec exactement ce
     * secret, aucune écriture n'a lieu. Sinon, tout facteur existant
     * (actif ou en attente) est révoqué et remplacé par un nouveau facteur
     * actif portant {@code base32Secret} — la base MySQL de démonstration
     * étant persistante d'un démarrage à l'autre, un changement de la
     * valeur configurée doit rester pris en compte au démarrage suivant,
     * exactement comme {@link DemoAccountProvisioner#ensureActiveAccount}
     * resynchronise le mot de passe.
     *
     * @param email        adresse du compte fictif, déjà provisionné
     * @param base32Secret secret TOTP en base32 (RFC 4648) ; jamais une
     *                     valeur de production, jamais journalisé en clair
     * @throws IllegalStateException    si aucun compte n'existe pour cette adresse
     * @throws IllegalArgumentException si {@code base32Secret} n'est pas un
     *                                  base32 valide
     */
    void ensureDeterministicTotp(String email, String base32Secret);
}
