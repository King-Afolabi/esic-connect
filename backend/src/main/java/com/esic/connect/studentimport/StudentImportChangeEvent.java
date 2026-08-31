package com.esic.connect.studentimport;

import java.util.UUID;

/**
 * Événement d'audit d'un import CSV d'apprenants. Consommé par le module
 * {@code audit} uniquement (rapport §4.4, §10).
 *
 * <p>Ne transporte <strong>aucune donnée personnelle</strong> : ni
 * adresse électronique, ni nom, ni numéro étudiant, ni valeur de cellule,
 * ni adresse IP (cahier §30.3). Seulement l'identifiant public du job,
 * l'auteur (identifiant interne), l'action et un complément non sensible
 * ({@code detail}), par exemple
 * {@code "job=<uuid>;created=12;updated=3;moved=1;invited=12;ignored=0"}.
 *
 * @param jobPublicId identifiant public du job d'import
 * @param actorUserId auteur de l'action (identifiant interne), {@code null} si non résolu
 * @param action      action réalisée
 * @param detail      complément non sensible, {@code null} si sans objet
 */
public record StudentImportChangeEvent(
        UUID jobPublicId,
        Long actorUserId,
        StudentImportChangeAction action,
        String detail) {
}
