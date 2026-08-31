package com.esic.connect.studentimport.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de réponse de l'API d'import (rapport §8). Aucun identifiant SQL
 * interne, aucun jeton, aucun hachage de mot de passe ; la valeur de
 * cellule tronquée ({@code receivedValue}) est rendue à la revue mais
 * jamais reprise dans l'audit.
 */
final class StudentImportResponses {

    private StudentImportResponses() {
    }

    /**
     * @param publicId      identifiant public du job
     * @param status        {@code SIMULATED} / {@code APPLIED} / {@code CANCELLED} / {@code EXPIRED}
     * @param fileName       nom d'origine assaini
     * @param fileSha256     empreinte du contenu reçu (contenu non conservé)
     * @param fileSizeBytes  taille du fichier reçu
     * @param csvSeparator   séparateur retenu
     * @param scopeProgramCode filtre de périmètre éventuel
     * @param scopeClassCode   filtre de périmètre éventuel
     * @param confirmable    {@code true} si la simulation peut être confirmée
     * @param summary        bilan chiffré (AC-004)
     * @param issues         anomalies globales
     * @param simulatedAt    date de simulation
     * @param expiresAt      échéance de la simulation
     * @param confirmedAt    date de confirmation ({@code null} tant que non appliqué)
     * @param appliedSummary bilan appliqué ({@code null} tant que non appliqué)
     * @param createdAt      création
     */
    record JobResponse(
            UUID publicId,
            String status,
            String fileName,
            String fileSha256,
            long fileSizeBytes,
            String csvSeparator,
            String scopeProgramCode,
            String scopeClassCode,
            boolean confirmable,
            Summary summary,
            List<JobIssueResponse> issues,
            Instant simulatedAt,
            Instant expiresAt,
            Instant confirmedAt,
            AppliedSummary appliedSummary,
            Instant createdAt) {
    }

    /**
     * Bilan de simulation (rapport §6, AC-004).
     *
     * @param total     lignes de données analysées
     * @param valid     lignes sans anomalie
     * @param warning   lignes avec au moins un avertissement, sans erreur
     * @param error     lignes avec au moins une erreur
     * @param blocking  anomalies globales bloquantes
     * @param plannedCreate    lignes prévues en création de compte
     * @param plannedUpdate    lignes prévues en inscription d'un compte existant ou mise à jour de profil
     * @param plannedTransfer  lignes prévues en changement de classe
     * @param plannedNoop      lignes sans changement
     */
    record Summary(
            int total,
            int valid,
            int warning,
            int error,
            int blocking,
            int plannedCreate,
            int plannedUpdate,
            int plannedTransfer,
            int plannedNoop) {
    }

    /**
     * @param created     comptes créés + invités
     * @param updated     inscriptions d'un compte existant + mises à jour de profil
     * @param transferred changements de classe
     * @param invited     invitations (r)émises
     * @param ignored     lignes sans changement
     */
    record AppliedSummary(Integer created, Integer updated, Integer transferred, Integer invited, Integer ignored) {
    }

    /**
     * @param severity   {@code INFO} / {@code WARNING} / {@code ERROR} / {@code BLOCKING}
     * @param code       code {@code IMP_*}
     * @param message    message lisible
     * @param columnName colonne concernée ({@code null} si transverse)
     */
    record JobIssueResponse(String severity, String code, String message, String columnName) {
    }

    /**
     * @param publicId       identifiant public de la ligne
     * @param rowNumber      n° de la ligne dans le fichier
     * @param rowStatus      {@code VALID} / {@code WARNING} / {@code ERROR}
     * @param plannedAction  action calculée
     * @param lastName       nom normalisé
     * @param firstName      prénom normalisé
     * @param email          e-mail normalisé
     * @param phone          téléphone normalisé ({@code null} si absent)
     * @param formationCode  code de formation normalisé
     * @param classCode      code de classe normalisé
     * @param academicYear   année scolaire normalisée
     * @param studentNumber  numéro étudiant du fichier ({@code null} si à générer)
     * @param birthDate      date de naissance analysée ({@code null} si absente / illisible)
     * @param workStudy      alternance ({@code null} si absente / illisible)
     * @param companyName    entreprise ({@code null} si absente)
     * @param resolvedClassPublicId      classe résolue ({@code null} si non résolue)
     * @param resolvedUserPublicId       compte rapproché ({@code null} si aucun)
     * @param resolvedEnrollmentPublicId inscription courante ({@code null} hors changement de classe)
     * @param studentNumberGenerated     {@code true} si un numéro sera généré à la confirmation
     * @param appliedOutcome             résultat effectif ({@code null} tant que non appliqué)
     * @param issues                     anomalies de la ligne
     */
    record RowResponse(
            UUID publicId,
            int rowNumber,
            String rowStatus,
            String plannedAction,
            String lastName,
            String firstName,
            String email,
            String phone,
            String formationCode,
            String classCode,
            String academicYear,
            String studentNumber,
            java.time.LocalDate birthDate,
            Boolean workStudy,
            String companyName,
            UUID resolvedClassPublicId,
            UUID resolvedUserPublicId,
            UUID resolvedEnrollmentPublicId,
            boolean studentNumberGenerated,
            String appliedOutcome,
            List<RowIssueResponse> issues) {
    }

    /**
     * @param severity      gravité
     * @param code          code {@code IMP_*}
     * @param message       message lisible
     * @param columnName    colonne concernée ({@code null} si transverse)
     * @param receivedValue valeur reçue tronquée ({@code null} si sans objet) — jamais dans l'audit
     * @param suggestedValue correction attendue ({@code null} si sans objet)
     */
    record RowIssueResponse(String severity, String code, String message, String columnName,
                            String receivedValue, String suggestedValue) {
    }
}
