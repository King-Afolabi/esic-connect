package com.esic.connect.enrollment.internal;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Génère un numéro étudiant {@code ESIC-{année}-{séquence zéro-padée}}
 * quand la création manuelle d'un profil apprenant laisse le champ vide.
 *
 * <p>Même mécanisme et même table ({@code student_number_sequence}) que
 * l'allocation faite à la confirmation d'un import de masse
 * ({@code studentimport}) : l'{@code INSERT ... ON DUPLICATE KEY UPDATE}
 * prend un verrou de ligne sur {@code start_year}, ce qui sérialise deux
 * allocations concurrentes visant la même année, et le rollback de la
 * transaction appelante annule l'incrément (aucune valeur « brûlée »).
 * L'unicité SQL {@code uq_student_profile_student_number} reste la seule
 * autorité — la génération n'est qu'un pré-remplissage.
 *
 * <p>Pas d'entité JPA ni de repository dédiés ici : l'accès natif via
 * {@link EntityManager} suffit et évite un second mapping de la table
 * {@code student_number_sequence} dans un autre module.
 */
// Nom de bean explicite : le module `studentimport` a une classe
// homonyme (`studentNumberAllocator`) et le nom court par défaut de
// Spring entrerait en conflit.
@Component("enrollmentStudentNumberAllocator")
class StudentNumberAllocator {

    /** Largeur du compteur zéro-padé — aligné sur {@code app.import.student.number-sequence-width} (défaut 5). */
    private static final int WIDTH = 5;
    private static final int UPPER_BOUND = (int) Math.pow(10, WIDTH);

    private final EntityManager entityManager;
    private final Clock clock;

    StudentNumberAllocator(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Alloue et formate le prochain numéro pour l'année civile courante.
     * À appeler dans la transaction de la création du profil.
     *
     * @throws EnrollmentException {@link EnrollmentException.Kind#STUDENT_NUMBER_EXHAUSTED}
     *                             si la borne de largeur est atteinte pour l'année
     */
    @Transactional
    String allocate() {
        int startYear = LocalDate.now(clock).getYear();
        Timestamp now = Timestamp.from(clock.instant());
        entityManager.createNativeQuery(
                        "INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
                                + "VALUES (:startYear, 2, :now) "
                                + "ON DUPLICATE KEY UPDATE next_value = next_value + 1, updated_at = :now")
                .setParameter("startYear", startYear)
                .setParameter("now", now)
                .executeUpdate();
        Number next = (Number) entityManager.createNativeQuery(
                        "SELECT next_value FROM student_number_sequence WHERE start_year = :startYear")
                .setParameter("startYear", startYear)
                .getSingleResult();
        int allocated = next.intValue() - 1;
        if (allocated >= UPPER_BOUND) {
            throw new EnrollmentException(EnrollmentException.Kind.STUDENT_NUMBER_EXHAUSTED);
        }
        return String.format(Locale.ROOT, "ESIC-%04d-%0" + WIDTH + "d", startYear, allocated);
    }
}
