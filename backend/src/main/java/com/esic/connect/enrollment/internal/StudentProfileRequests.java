package com.esic.connect.enrollment.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API des profils apprenants. */
final class StudentProfileRequests {

    private StudentProfileRequests() {
    }

    /**
     * Création. {@code userPublicId} en forme UUID (compte existant, non
     * archivé, porteur d'un rôle actif {@code STUDENT}) ;
     * {@code studentNumber} <strong>facultatif</strong> : laissé vide, il
     * est généré au format normalisé {@code ESIC-{année}-{séquence}}
     * (docs/04 §3.5, §9.2) ; fourni, il doit rester unique ;
     * {@code birthDate} et {@code companyName} facultatifs ;
     * {@code workStudy} par défaut {@code false}.
     */
    record Create(
            @NotBlank @Size(max = 40) String userPublicId,
            @Size(max = 50) String studentNumber,
            LocalDate birthDate,
            Boolean workStudy,
            @Size(max = 191) String companyName) {
    }
}
