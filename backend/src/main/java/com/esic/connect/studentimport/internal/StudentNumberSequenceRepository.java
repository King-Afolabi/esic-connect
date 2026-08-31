package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface StudentNumberSequenceRepository extends JpaRepository<StudentNumberSequence, Integer> {

    /**
     * Allocation atomique d'un numéro pour {@code startYear} — rapport §3.2.
     * Exécutée <strong>dans la transaction de la confirmation</strong>
     * (propagation {@code REQUIRED}) : l'{@code UPDATE} prend un verrou de
     * ligne sur {@code start_year}, ce qui sérialise deux confirmations
     * concurrentes visant la même année ; un rollback de la confirmation
     * annule aussi cet {@code UPDATE} (aucune valeur « brûlée »).
     *
     * <p>Après l'appel, la valeur allouée est {@code next_value - 1}
     * (relire via {@link #findById(Object)} dans la même transaction).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
            + "VALUES (:startYear, 2, :now) "
            + "ON DUPLICATE KEY UPDATE next_value = next_value + 1, updated_at = :now", nativeQuery = true)
    void bump(@Param("startYear") int startYear, @Param("now") Instant now);

    /**
     * Lecture <strong>native</strong> de {@code next_value} — contourne le
     * cache de premier niveau JPA pour relire la valeur écrite par
     * {@link #bump} dans la même transaction sans détacher les autres
     * entités.
     */
    @Query(value = "SELECT next_value FROM student_number_sequence WHERE start_year = :startYear", nativeQuery = true)
    Integer selectNextValue(@Param("startYear") int startYear);
}
