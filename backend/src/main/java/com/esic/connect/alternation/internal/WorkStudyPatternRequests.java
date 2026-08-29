package com.esic.connect.alternation.internal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Requêtes de l'API des modèles de rythme. */
final class WorkStudyPatternRequests {

    private WorkStudyPatternRequests() {
    }

    /**
     * Création. {@code code} immuable après création ; {@code type} validé
     * ici puis revérifié côté service. {@code cycleLengthWeeks} facultatif
     * (normalisé selon le type : 1 pour 3j/2j, 4 pour les rythmes
     * semaine/4, obligatoire pour {@code CUSTOM}). {@code configuration} =
     * objet JSON (contrat détaillé par type dans
     * {@link AlternationConfigParser}) : toute propriété inconnue ou
     * incohérente est refusée.
     */
    record Create(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 191) String name,
            @Size(max = 500) String description,
            @NotBlank @Pattern(regexp = "THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY|ONE_WEEK_SCHOOL_OUT_OF_FOUR"
                    + "|TWO_WEEKS_SCHOOL_OUT_OF_FOUR|CUSTOM",
                    message = "type de rythme invalide") String type,
            @Positive Integer cycleLengthWeeks,
            @NotNull JsonNode configuration) {
    }

    /**
     * Mise à jour du nom, de la description et de la configuration. Le
     * {@code code} et le {@code type} restent figés.
     */
    record Update(
            @NotBlank @Size(max = 191) String name,
            @Size(max = 500) String description,
            @Positive Integer cycleLengthWeeks,
            @NotNull JsonNode configuration) {
    }

    /** Archivage : motif obligatoire (piste d'audit). */
    record Archive(
            @NotBlank @Size(max = 500) String reason) {
    }
}
