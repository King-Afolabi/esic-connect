package com.esic.connect.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Politique de mot de passe (docs/02-cahier-des-charges.md §17.1 ;
 * RG-010).
 *
 * <p>Le cahier impose une longueur minimale, le blocage des mots de passe
 * courants, et interdit toute expiration périodique arbitraire. Cette
 * classe applique ces règles et rien de plus : pas d'exigence de
 * composition (majuscule, chiffre, caractère spécial), qui pousse en
 * pratique à des variantes prévisibles sans gain réel d'entropie.
 *
 * <p>La liste embarquée couvre les mots de passe les plus fréquemment
 * observés dans les fuites publiques, francophones inclus. Elle est
 * volontairement courte et lisible ; un déploiement réel peut la
 * remplacer par une liste étendue chargée depuis un fichier.
 */
@Component
public class PasswordPolicy {

    /** Longueur minimale par défaut, alignée sur les recommandations actuelles. */
    private static final int DEFAULT_MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 200;

    /**
     * Mots de passe les plus fréquemment observés dans les fuites
     * publiques, francophones inclus.
     *
     * <p>La liste contient volontairement des entrées <strong>plus courtes
     * que la longueur minimale</strong> : elles ne peuvent pas être
     * atteintes tant que le minimum vaut 12, mais elles restent utiles si
     * un déploiement abaisse ce seuil, et elles documentent l'intention.
     *
     * <p>Elle contient surtout les variantes <strong>longues</strong> —
     * celles que produisent réellement les gens contraints à 12
     * caractères : un mot courant suivi de chiffres, ou deux mots
     * courants collés. Sans elles, la liste serait décorative.
     */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            // Variantes courtes (inatteignables au minimum actuel).
            "123456", "123456789", "12345678", "1234567890", "azerty", "azertyuiop",
            "qwerty", "qwertyuiop", "motdepasse", "password", "passw0rd", "password1",
            "iloveyou", "admin", "bonjour", "soleil", "chouchou", "doudou",
            "bienvenue", "welcome", "letmein", "monkey", "dragon", "football",
            "abc123", "abcd1234", "111111", "000000", "123123", "654321", "555555",
            "azerty123", "qwerty123", "1q2w3e4r", "1qaz2wsx", "zaq12wsx", "trustno1",
            "changeme", "secret", "sunshine", "princess", "superman", "batman",
            "esic", "connect", "etudiant", "apprenant", "formateur", "professeur",
            // Variantes de douze caractères ou plus — les seules réellement
            // atteignables avec la politique en vigueur.
            "motdepasse1", "motdepasse12", "motdepasse123", "motdepasse1234",
            "motdepasse2025", "motdepasse2026", "motdepasse!123", "password123",
            "password1234", "password12345", "password2026", "azertyuiop123",
            "azertyuiop1234", "azerty123456", "qwertyuiop123", "qwerty123456",
            "123456789012", "1234567890123", "administrateur", "administrateur1",
            "bonjour123456", "soleil123456", "iloveyou1234", "chocolat1234",
            "coucou123456", "sansmotdepasse", "jenesaispas123", "esicconnect",
            "esicconnect1", "esicconnect123", "esicconnect2026", "esic-connect",
            "motdepassesecret", "monmotdepasse", "monmotdepasse1", "changemenow123",
            "welcome123456", "letmein123456", "qwertyazerty", "azertyqwerty");

    private final int minLength;

    public PasswordPolicy(@Value("${app.security.password.min-length:12}") int minLength) {
        if (minLength < DEFAULT_MIN_LENGTH) {
            throw new IllegalStateException(
                    "app.security.password.min-length ne peut pas être inférieure à " + DEFAULT_MIN_LENGTH
                            + " (valeur reçue : " + minLength + ").");
        }
        this.minLength = minLength;
    }

    public int minLength() {
        return minLength;
    }

    /**
     * Vérifie un mot de passe candidat.
     *
     * @param rawPassword le mot de passe en clair
     * @param email       l'adresse du compte, pour interdire de la
     *                    réutiliser comme mot de passe ; peut être nul
     * @return la liste des motifs de refus, vide si le mot de passe est
     *         acceptable
     */
    public List<String> violations(String rawPassword, String email) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return List.of("Le mot de passe est obligatoire.");
        }
        if (rawPassword.length() < minLength) {
            return List.of("Le mot de passe doit contenir au moins " + minLength + " caractères.");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            // Borne haute : au-delà, le coût de hachage devient un levier
            // de déni de service (BCrypt tronque de toute façon à 72 octets).
            return List.of("Le mot de passe ne peut pas dépasser " + MAX_LENGTH + " caractères.");
        }

        String canonical = canonical(rawPassword);
        if (COMMON_PASSWORDS.contains(canonical)) {
            return List.of("Ce mot de passe est trop courant. Choisissez-en un autre.");
        }
        if (isSingleRepeatedCharacter(canonical)) {
            return List.of("Ce mot de passe est trop simple. Choisissez-en un autre.");
        }
        if (email != null && !email.isBlank()) {
            String localPart = canonical(email.split("@")[0]);
            if (localPart.length() >= 3 && canonical.contains(localPart)) {
                return List.of("Le mot de passe ne doit pas contenir votre adresse électronique.");
            }
        }
        return List.of();
    }

    private static boolean isSingleRepeatedCharacter(String value) {
        return value.chars().distinct().count() == 1;
    }

    /** Minuscules, accents retirés : « MotDePasse » et « môtdepasse » se valent. */
    private static String canonical(String value) {
        String lowercased = value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lowercased, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
