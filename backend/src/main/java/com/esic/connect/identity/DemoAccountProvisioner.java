package com.esic.connect.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Port public d'amorçage <strong>réservé au profil {@code demo}</strong>.
 *
 * <p>Permet à un initialiseur de démonstration de créer, de façon
 * idempotente, des comptes <em>fictifs</em> déjà {@code ACTIVE} avec leurs
 * rôles — sans passer par le parcours d'invitation (chicken-and-egg : il
 * n'existe encore aucun compte administrateur pour émettre une
 * invitation). L'implémentation n'est enregistrée que sous le profil
 * {@code demo} : ce n'est pas une porte dérobée en production.
 *
 * <p>Le mot de passe fourni est haché par le {@code PasswordEncoder} réel
 * de l'application. Il ne doit jamais être une valeur de production et
 * n'est jamais journalisé par l'implémentation.
 */
public interface DemoAccountProvisioner {

    /**
     * Garantit l'existence d'un compte {@code ACTIVE} pour {@code email},
     * avec au moins les rôles demandés (actifs).
     *
     * <p>Idempotent : un second appel ne crée jamais de doublon (compte
     * ou rôle) et ne supprime aucune dépendance (rôles déjà présents,
     * profil apprenant, inscriptions, audit...).
     *
     * <p><strong>Profil {@code demo} :</strong> comme la base MySQL est
     * persistante d'un démarrage à l'autre, l'implémentation
     * <em>resynchronise</em> chaque compte fictif existant sur la valeur
     * <em>courante</em> de {@code rawPassword} — le hachage est réécrit
     * seulement s'il ne correspond plus — et ramène le compte à un état
     * permettant la connexion (statut {@code ACTIVE}, suspension levée).
     * Fonctionnellement idempotent : avec le même mot de passe qu'au
     * démarrage précédent, aucun champ n'est réécrit. Ce comportement de
     * synchronisation n'existe que sous le profil {@code demo}.
     *
     * @param email        adresse (fictive, domaine réservé — ex. {@code example.test})
     * @param firstName    prénom fictif
     * @param lastName      nom fictif
     * @param rawPassword   mot de passe de démonstration en clair (haché ensuite)
     * @param roleCodes     codes de rôle ({@code "ADMIN"}, {@code "TEACHER"}, {@code "STUDENT"}...)
     * @return l'identifiant public du compte (existant ou créé)
     */
    UUID ensureActiveAccount(String email, String firstName, String lastName, String rawPassword,
                             Set<String> roleCodes);
}
