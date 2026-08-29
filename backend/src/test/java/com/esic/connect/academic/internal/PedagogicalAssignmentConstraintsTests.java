package com.esic.connect.academic.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL de {@code pedagogical_assignment} (V6) : un seul
 * {@code PRIMARY_MANAGER} actif par formation (colonne générée
 * {@code active_primary_key}), créneau libéré par une clôture, absence de
 * limite sur les {@code DELEGATE}, {@code CHECK} de période
 * ({@code valid_until >= valid_from}, en {@link LocalDate}), unicité de
 * {@code public_id} et clés étrangères {@code RESTRICT} (dont
 * {@code delegated_by_id}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PedagogicalAssignmentConstraintsTests {

    @Autowired
    private PedagogicalAssignmentRepository assignmentRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void onlyOneActivePrimaryManagerPerProgram() {
        Program programA = programRepository.saveAndFlush(program(shortCode()));
        Program programB = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();

        assignmentRepository.saveAndFlush(primary(programA, manager));
        // Même rôle principal, autre formation : accepté.
        assignmentRepository.saveAndFlush(primary(programB, manager));

        // Deuxième PRIMARY_MANAGER actif sur la formation A : rejeté (test
        // en dernier — la violation invalide la transaction courante).
        assertThrows(DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(primary(programA, manager)));
    }

    @Test
    void activePrimaryConstraintCollisionIsRecognisedFromARealException() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        assignmentRepository.saveAndFlush(primary(program, manager));

        DataIntegrityViolationException collision = assertThrows(DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(primary(program, manager)));
        assertThat(PedagogicalAssignmentService.isActivePrimaryUniqueViolation(collision)).isTrue();
    }

    @Test
    void unrelatedIntegrityViolationIsNotRecognisedAsPrimaryConflict() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        PedagogicalAssignment first = assignmentRepository.saveAndFlush(delegate(program, manager));
        PedagogicalAssignment duplicatePublicId = delegate(program, manager);
        ReflectionTestUtils.setField(duplicatePublicId, "publicId", first.getPublicId());

        // Violation de `uq_pedagogical_assignment_public_id` (et non de la
        // contrainte du responsable principal) : ne doit PAS être mappée.
        DataIntegrityViolationException other = assertThrows(DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(duplicatePublicId));
        assertThat(PedagogicalAssignmentService.isActivePrimaryUniqueViolation(other)).isFalse();
    }

    @Test
    void delegatesAreNotLimitedForAProgram() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        assignmentRepository.saveAndFlush(delegate(program, manager));
        assertDoesNotThrow(() -> assignmentRepository.saveAndFlush(delegate(program, manager)));
    }

    @Test
    void closingPrimaryFreesTheSlot() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        PedagogicalAssignment first = assignmentRepository.saveAndFlush(primary(program, manager));

        first.close("changement", null, LocalDate.now());
        assignmentRepository.saveAndFlush(first);

        assertDoesNotThrow(() -> assignmentRepository.saveAndFlush(primary(program, manager)));
    }

    @Test
    void periodCheckRejectsValidUntilBeforeValidFrom() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        LocalDate from = LocalDate.of(2026, 9, 1);
        PedagogicalAssignment invalid = new PedagogicalAssignment(program, manager,
                PedagogicalAssignmentRole.DELEGATE, from, from.minusDays(1), null, null);
        // CHECK (valid_until IS NULL OR valid_until >= valid_from) : violation
        // traduite en DataIntegrityViolationException ou JpaSystemException
        // selon le code d'erreur SQL — dans tous les cas une DataAccessException.
        assertThrows(DataAccessException.class,
                () -> assignmentRepository.saveAndFlush(invalid));
    }

    @Test
    void sameDayValidityIsAccepted() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        LocalDate day = LocalDate.of(2026, 9, 1);
        assertDoesNotThrow(() -> assignmentRepository.saveAndFlush(new PedagogicalAssignment(program, manager,
                PedagogicalAssignmentRole.DELEGATE, day, day, null, null)));
    }

    @Test
    void publicIdMustBeUnique() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        PedagogicalAssignment first = assignmentRepository.saveAndFlush(delegate(program, manager));
        PedagogicalAssignment second = delegate(program, manager);
        ReflectionTestUtils.setField(second, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(second));
    }

    @Test
    void managerForeignKeyIsRestrict() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        assignmentRepository.saveAndFlush(delegate(program, manager));

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM user_account WHERE id = :id")
                    .setParameter("id", manager)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void delegatedByForeignKeyIsRestrict() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        long delegatedBy = insertUser();
        assignmentRepository.saveAndFlush(new PedagogicalAssignment(program, manager,
                PedagogicalAssignmentRole.DELEGATE, LocalDate.now(), null, "motif", delegatedBy));

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM user_account WHERE id = :id")
                    .setParameter("id", delegatedBy)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void programForeignKeyIsRestrict() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        long manager = insertUser();
        assignmentRepository.saveAndFlush(delegate(program, manager));

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM program WHERE id = :id")
                    .setParameter("id", program.getId())
                    .executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------

    private long insertUser() {
        EntityManager em = entityManager.getEntityManager();
        String email = "pm-" + UUID.randomUUID() + "@esic-connect.test";
        em.createNativeQuery("""
                        INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                                  created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Ped', 'Manager', 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("email", email)
                .executeUpdate();
        Object id = em.createNativeQuery("SELECT id FROM user_account WHERE email = :email")
                .setParameter("email", email)
                .getSingleResult();
        return ((Number) id).longValue();
    }

    private static PedagogicalAssignment primary(Program program, long manager) {
        return new PedagogicalAssignment(program, manager, PedagogicalAssignmentRole.PRIMARY_MANAGER,
                LocalDate.now(), null, null, null);
    }

    private static PedagogicalAssignment delegate(Program program, long manager) {
        return new PedagogicalAssignment(program, manager, PedagogicalAssignmentRole.DELEGATE,
                LocalDate.now(), null, null, null);
    }

    private static Program program(String code) {
        return new Program(code, code, ProgramType.BTS, null);
    }

    private static String shortCode() {
        return "AC-" + UUID.randomUUID().toString().substring(0, 20);
    }
}
