package com.esic.connect.studentimport.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.JDBCException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL et mapping JPA de la migration {@code V11} (module
 * {@code studentimport}, rapport §7 / §14.2) :
 * <ul>
 *   <li>{@code CHECK} de {@code student_import_job.status},
 *       {@code student_import_job.file_size_bytes},
 *       {@code student_import_job_issue.severity},
 *       {@code student_import_row.row_status},
 *       {@code student_import_row.planned_action},
 *       {@code student_import_row_issue.severity},
 *       {@code student_number_sequence.next_value} ;</li>
 *   <li>{@code student_import_job.status} : exactement {@code SIMULATED},
 *       {@code APPLIED}, {@code CANCELLED}, {@code EXPIRED} — {@code FAILED}
 *       rejeté par la contrainte comme par l'enum ;</li>
 *   <li>mapping de la colonne réservée {@code `row_number`} (MySQL 8) ;</li>
 *   <li>unicité {@code (student_import_job_id, row_number)} ;</li>
 *   <li>{@code ON DELETE CASCADE} {@code job -> job_issue} et
 *       {@code job -> row -> row_issue} ;</li>
 *   <li>clés étrangères {@code RESTRICT} vers {@code user_account}
 *       ({@code requested_by_id}, {@code confirmed_by_id}) ;</li>
 *   <li>unicité {@code public_id} des quatre tables ;</li>
 *   <li>clé primaire {@code student_number_sequence.start_year} et
 *       {@code INSERT ... ON DUPLICATE KEY UPDATE} incrémentant
 *       {@code next_value} ;</li>
 *   <li>aller-retour de persistance (mapping colonnes typées).</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class StudentImportSchemaConstraintsTests {

    @Autowired
    private StudentImportJobRepository jobRepository;
    @Autowired
    private StudentImportJobIssueRepository jobIssueRepository;
    @Autowired
    private StudentImportRowRepository rowRepository;
    @Autowired
    private StudentImportRowIssueRepository rowIssueRepository;
    @Autowired
    private StudentNumberSequenceRepository sequenceRepository;
    @Autowired
    private TestEntityManager entityManager;

    private long requesterId;

    @BeforeEach
    void seedRequester() {
        requesterId = insertUser();
    }

    // ------------------------------------------------------------------
    // CHECK — student_import_job
    // ------------------------------------------------------------------

    @Test
    void jobStatusCheckRejectsUnknownValue() {
        long jobId = jobRepository.saveAndFlush(newJob()).getId();
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_job SET status = 'BOGUS' WHERE id = :id")
                    .setParameter("id", jobId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void jobFileSizeCheckRejectsZero() {
        long jobId = jobRepository.saveAndFlush(newJob()).getId();
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_job SET file_size_bytes = 0 WHERE id = :id")
                    .setParameter("id", jobId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void jobStatusCheckAllowsExactlyTheFourV11Values() {
        long jobId = jobRepository.saveAndFlush(newJob()).getId();
        for (StudentImportJobStatus status : StudentImportJobStatus.values()) {
            assertDoesNotThrow(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE student_import_job SET status = :s WHERE id = :id")
                        .setParameter("s", status.name())
                        .setParameter("id", jobId)
                        .executeUpdate();
                entityManager.flush();
            }, "V11 chk_student_import_job_status doit accepter " + status);
        }
        // Le statut FAILED, retiré de l'enum, n'est pas dans la contrainte V11.
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_job SET status = 'FAILED' WHERE id = :id")
                    .setParameter("id", jobId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void jobStatusEnumHasExactlyTheFourStatesAuthorisedByV11() {
        assertThat(StudentImportJobStatus.values())
                .containsExactly(StudentImportJobStatus.SIMULATED, StudentImportJobStatus.APPLIED,
                        StudentImportJobStatus.CANCELLED, StudentImportJobStatus.EXPIRED);
    }

    // ------------------------------------------------------------------
    // CHECK — issues / row
    // ------------------------------------------------------------------

    @Test
    void jobIssueSeverityCheckRejectsUnknownValue() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        long issueId = jobIssueRepository.saveAndFlush(new StudentImportJobIssue(job,
                StudentImportIssueSeverity.BLOCKING, "IMP_MISSING_COLUMN",
                "colonne obligatoire absente", "email")).getId();
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_job_issue SET severity = 'BOGUS' WHERE id = :id")
                    .setParameter("id", issueId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void rowStatusCheckRejectsUnknownValue() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        long rowId = rowRepository.saveAndFlush(newRow(job, 2)).getId();
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_row SET row_status = 'BOGUS' WHERE id = :id")
                    .setParameter("id", rowId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void rowPlannedActionCheckRejectsUnknownValue() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        long rowId = rowRepository.saveAndFlush(newRow(job, 2)).getId();
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_row SET planned_action = 'BOGUS' WHERE id = :id")
                    .setParameter("id", rowId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void rowIssueSeverityCheckRejectsUnknownValue() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        StudentImportRow row = rowRepository.saveAndFlush(newRow(job, 2));
        long issueId = rowIssueRepository.saveAndFlush(new StudentImportRowIssue(row,
                StudentImportIssueSeverity.WARNING, "IMP_PHONE_FORMAT",
                "téléphone non conforme", "phone", "12", null)).getId();
        assertThrows(JDBCException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE student_import_row_issue SET severity = 'BOGUS' WHERE id = :id")
                    .setParameter("id", issueId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------
    // Unicité (job_id, row_number)
    // ------------------------------------------------------------------

    @Test
    void rowNumberIsUniquePerJob() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        rowRepository.saveAndFlush(newRow(job, 2));
        assertDoesNotThrow(() -> rowRepository.saveAndFlush(newRow(job, 3)));
        assertThrows(DataIntegrityViolationException.class, () -> rowRepository.saveAndFlush(newRow(job, 2)));
    }

    @Test
    void sameRowNumberInAnotherJobIsAccepted() {
        StudentImportJob first = jobRepository.saveAndFlush(newJob());
        StudentImportJob second = jobRepository.saveAndFlush(newJob());
        rowRepository.saveAndFlush(newRow(first, 2));
        assertDoesNotThrow(() -> rowRepository.saveAndFlush(newRow(second, 2)));
    }

    @Test
    void rowNumberMapsToTheQuotedReservedColumn() {
        // La colonne physique s'appelle bien `row_number` (mot réservé MySQL 8),
        // et le mapping @Column(name = "`row_number`") écrit / relit cette colonne.
        Number columnCount = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'student_import_row' "
                        + "AND column_name = 'row_number'")
                .getSingleResult();
        assertThat(columnCount.intValue()).isEqualTo(1);

        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        long rowId = rowRepository.saveAndFlush(newRow(job, 7)).getId();
        entityManager.flush();
        entityManager.clear();

        Number stored = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT `row_number` FROM student_import_row WHERE id = :id")
                .setParameter("id", rowId)
                .getSingleResult();
        assertThat(stored.intValue()).isEqualTo(7);
        assertThat(rowRepository.findById(rowId).orElseThrow().getRowNumber()).isEqualTo(7);
    }

    // ------------------------------------------------------------------
    // ON DELETE CASCADE
    // ------------------------------------------------------------------

    @Test
    void deletingAJobCascadesToIssuesRowsAndRowIssues() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        jobIssueRepository.saveAndFlush(new StudentImportJobIssue(job, StudentImportIssueSeverity.WARNING,
                "IMP_UNKNOWN_COLUMN", "colonne inconnue ignorée", "note"));
        StudentImportRow row = rowRepository.saveAndFlush(newRow(job, 2));
        rowIssueRepository.saveAndFlush(new StudentImportRowIssue(row, StudentImportIssueSeverity.ERROR,
                "IMP_EMAIL_INVALID", "adresse invalide", "email", "pas-un-email", null));
        long jobId = job.getId();
        long rowId = row.getId();
        entityManager.flush();
        entityManager.clear();

        entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM student_import_job WHERE id = :id")
                .setParameter("id", jobId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(jobRepository.findById(jobId)).isEmpty();
        assertThat(jobIssueRepository.countByJobId(jobId)).isZero();
        assertThat(rowRepository.countByJobId(jobId)).isZero();
        assertThat(rowIssueRepository.countByRowId(rowId)).isZero();
    }

    // ------------------------------------------------------------------
    // FK RESTRICT vers user_account
    // ------------------------------------------------------------------

    @Test
    void requestedByForeignKeyIsRestrict() {
        jobRepository.saveAndFlush(newJob());
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM user_account WHERE id = :id")
                    .setParameter("id", requesterId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void confirmedByForeignKeyIsRestrict() {
        long confirmerId = insertUser();
        long jobId = jobRepository.saveAndFlush(newJob()).getId();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE student_import_job SET confirmed_by_id = :confirmer WHERE id = :id")
                .setParameter("confirmer", confirmerId)
                .setParameter("id", jobId)
                .executeUpdate();
        entityManager.flush();

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM user_account WHERE id = :id")
                    .setParameter("id", confirmerId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------
    // Unicité public_id
    // ------------------------------------------------------------------

    @Test
    void jobPublicIdIsUnique() {
        StudentImportJob first = jobRepository.saveAndFlush(newJob());
        StudentImportJob clash = newJob();
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> jobRepository.saveAndFlush(clash));
    }

    @Test
    void jobIssuePublicIdIsUnique() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        StudentImportJobIssue first = jobIssueRepository.saveAndFlush(new StudentImportJobIssue(job,
                StudentImportIssueSeverity.INFO, "IMP_X", "m", null));
        StudentImportJobIssue clash = new StudentImportJobIssue(job, StudentImportIssueSeverity.INFO, "IMP_Y", "m2", null);
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> jobIssueRepository.saveAndFlush(clash));
    }

    @Test
    void rowPublicIdIsUnique() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        StudentImportRow first = rowRepository.saveAndFlush(newRow(job, 2));
        StudentImportRow clash = newRow(job, 3);
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> rowRepository.saveAndFlush(clash));
    }

    @Test
    void rowIssuePublicIdIsUnique() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        StudentImportRow row = rowRepository.saveAndFlush(newRow(job, 2));
        StudentImportRowIssue first = rowIssueRepository.saveAndFlush(new StudentImportRowIssue(row,
                StudentImportIssueSeverity.WARNING, "IMP_X", "m", "phone", "x", null));
        StudentImportRowIssue clash = new StudentImportRowIssue(row, StudentImportIssueSeverity.WARNING,
                "IMP_Y", "m2", "phone", "y", null);
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> rowIssueRepository.saveAndFlush(clash));
    }

    // ------------------------------------------------------------------
    // student_number_sequence
    // ------------------------------------------------------------------

    @Test
    void sequenceNextValueCheckRejectsZero() {
        assertThrows(DataAccessException.class,
                () -> sequenceRepository.saveAndFlush(new StudentNumberSequence(2901, 0, Instant.now())));
    }

    @Test
    void sequenceStartYearIsPrimaryKey() {
        Instant now = Instant.now();
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
                        + "VALUES (2902, 5, :now)")
                .setParameter("now", now)
                .executeUpdate();
        entityManager.flush();

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
                            + "VALUES (2902, 7, :now)")
                    .setParameter("now", now)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void sequenceOnDuplicateKeyUpdateIncrementsNextValue() {
        Instant now = Instant.now();
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
                        + "VALUES (2903, 2, :now)")
                .setParameter("now", now)
                .executeUpdate();
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
                        + "VALUES (2903, 2, :now) "
                        + "ON DUPLICATE KEY UPDATE next_value = next_value + 1, updated_at = :now")
                .setParameter("now", now)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(sequenceRepository.findById(2903).orElseThrow().getNextValue()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // Mapping (aller-retour de persistance des colonnes typées)
    // ------------------------------------------------------------------

    @Test
    void entitiesRoundTripThroughTheSchema() {
        StudentImportJob job = jobRepository.saveAndFlush(newJob());
        StudentImportRow row = newRow(job, 4);
        UUID resolvedClass = UUID.randomUUID();
        ReflectionTestUtils.setField(row, "resolvedClassPublicId", resolvedClass);
        row.setNormalizedIdentity("Diant", "Étu", "etu.diant@esic-connect.test", "0102030405");
        row.setNormalizedTarget("PRG-DEMO", "C-DEMO", "2026-2027");
        rowRepository.saveAndFlush(row);
        jobIssueRepository.saveAndFlush(new StudentImportJobIssue(job, StudentImportIssueSeverity.INFO,
                "IMP_COLUMN_IGNORED", "colonne level_code ignorée", "level_code"));
        rowIssueRepository.saveAndFlush(new StudentImportRowIssue(row, StudentImportIssueSeverity.WARNING,
                "IMP_PHONE_FORMAT", "téléphone non conforme", "phone", "abc", "+33102030405"));
        sequenceRepository.saveAndFlush(new StudentNumberSequence(2904, 1, Instant.now()));
        long jobId = job.getId();
        long rowId = row.getId();
        entityManager.clear();

        StudentImportJob reloadedJob = jobRepository.findById(jobId).orElseThrow();
        assertThat(reloadedJob.getStatus()).isEqualTo(StudentImportJobStatus.SIMULATED);
        assertThat(reloadedJob.getOriginalFileName()).isEqualTo("apprenants.csv");
        assertThat(reloadedJob.getFileSha256()).hasSize(64);
        assertThat(reloadedJob.getCsvSeparator()).isEqualTo(',');
        assertThat(reloadedJob.getRequestedById()).isEqualTo(requesterId);
        assertThat(reloadedJob.isConfirmable()).isFalse();
        assertThat(reloadedJob.getCreatedAt()).isNotNull();
        assertThat(reloadedJob.getUpdatedAt()).isNotNull();

        StudentImportRow reloadedRow = rowRepository.findById(rowId).orElseThrow();
        assertThat(reloadedRow.getRowNumber()).isEqualTo(4);
        assertThat(reloadedRow.getInputEmail()).isEqualTo("etu.diant@esic-connect.test");
        assertThat(reloadedRow.getRowStatus()).isEqualTo(StudentImportRowStatus.VALID);
        assertThat(reloadedRow.getPlannedAction()).isEqualTo(StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL);
        assertThat(reloadedRow.isStudentNumberGenerated()).isFalse();
        assertThat(reloadedRow.getAppliedOutcome()).isNull();
        assertThat(ReflectionTestUtils.getField(reloadedRow, "resolvedClassPublicId")).isEqualTo(resolvedClass);

        assertThat(jobIssueRepository.countByJobId(jobId)).isEqualTo(1);
        assertThat(rowIssueRepository.countByRowId(rowId)).isEqualTo(1);
        assertThat(sequenceRepository.findById(2904).orElseThrow().getNextValue()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private StudentImportJob newJob() {
        Instant simulatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new StudentImportJob("apprenants.csv", "a".repeat(64), 1024, ',', requesterId,
                simulatedAt, simulatedAt.plus(7, ChronoUnit.DAYS));
    }

    private static StudentImportRow newRow(StudentImportJob job, int rowNumber) {
        return new StudentImportRow(job, rowNumber, StudentImportRowStatus.VALID,
                StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL);
    }

    private long insertUser() {
        EntityManager em = entityManager.getEntityManager();
        String email = "imp-" + UUID.randomUUID() + "@esic-connect.test";
        em.createNativeQuery("""
                INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                          created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Imp', 'Orter', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """)
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM user_account WHERE email = :email")
                .setParameter("email", email)
                .getSingleResult()).longValue();
    }
}
