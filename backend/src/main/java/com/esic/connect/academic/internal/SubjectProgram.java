package com.esic.connect.academic.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Rattachement d'une matière à une formation (docs/02 §6.4 : « une
 * matière peut être rattachée à une ou plusieurs formations »).
 *
 * <p>Table de liaison pure : ni identifiant public, ni verrouillage
 * optimiste. Elle n'est jamais exposée telle quelle — le contrat REST ne
 * connaît que la liste des formations d'une matière.
 */
@Entity
@Table(name = "subject_program")
@EntityListeners(AuditingEntityListener.class)
public class SubjectProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SubjectProgram() {
        // JPA
    }

    public SubjectProgram(Long subjectId, Long programId) {
        this.subjectId = subjectId;
        this.programId = programId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getProgramId() {
        return programId;
    }
}
