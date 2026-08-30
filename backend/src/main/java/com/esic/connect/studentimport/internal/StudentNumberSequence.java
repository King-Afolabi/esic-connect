package com.esic.connect.studentimport.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Compteur monotone par année de début d'année scolaire (V11, rapport
 * §3.2 / §7.5).
 *
 * <p>Alimenté uniquement pendant une confirmation d'import (dans sa
 * transaction, verrou de ligne sur {@code start_year}) pour générer un
 * numéro {@code ESIC-{annéeDébut}-{séquence}} quand la colonne CSV
 * {@code student_number} est absente. Ni {@code id} technique, ni
 * {@code public_id}, ni {@code version} : la clé primaire fonctionnelle
 * est {@code start_year}. Aucune clé étrangère. Jamais purgée.
 *
 * <p>Au checkpoint CP1, l'entité n'est écrite que par les tests de
 * contraintes ; l'allocation atomique
 * ({@code INSERT ... ON DUPLICATE KEY UPDATE}) relève des checkpoints
 * suivants.
 */
@Entity
@Table(name = "student_number_sequence")
class StudentNumberSequence {

    @Id
    @Column(name = "start_year", nullable = false, updatable = false)
    private Integer startYear;

    @Column(name = "next_value", nullable = false)
    private int nextValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentNumberSequence() {
        // JPA
    }

    StudentNumberSequence(int startYear, int nextValue, Instant updatedAt) {
        this.startYear = startYear;
        this.nextValue = nextValue;
        this.updatedAt = updatedAt;
    }

    Integer getStartYear() {
        return startYear;
    }

    int getNextValue() {
        return nextValue;
    }

    void setNextValue(int nextValue) {
        this.nextValue = nextValue;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
