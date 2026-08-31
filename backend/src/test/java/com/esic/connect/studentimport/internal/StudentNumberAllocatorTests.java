package com.esic.connect.studentimport.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Génération de numéro étudiant (rapport §3.2, §14.1) : format
 * {@code ESIC-{annéeDébut}-{NNNNN}} zéro-padé, incrément atomique via
 * {@code student_number_sequence}, borne de largeur → {@code STUDENT_NUMBER_EXHAUSTED}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, StudentNumberAllocator.class, StudentNumberAllocatorTests.TestConfig.class})
class StudentNumberAllocatorTests {

    static class TestConfig {
        @Bean
        StudentImportProperties properties() {
            return new StudentImportProperties(500, 2_097_152L, Duration.ofDays(7), Duration.ofDays(30), 5, 5);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private StudentNumberAllocator allocator;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void formatsWithFourDigitYearAndZeroPaddedSequence() {
        assertThat(allocator.format(2026, 42)).isEqualTo("ESIC-2026-00042");
        assertThat(allocator.format(2026, 1)).isEqualTo("ESIC-2026-00001");
        assertThat(allocator.format(2026, 12345)).isEqualTo("ESIC-2026-12345");
    }

    @Test
    void allocatesAConsecutiveSequencePerYear() {
        assertThat(allocator.allocate(2101)).isEqualTo("ESIC-2101-00001");
        assertThat(allocator.allocate(2101)).isEqualTo("ESIC-2101-00002");
        assertThat(allocator.allocate(2101)).isEqualTo("ESIC-2101-00003");
        assertThat(allocator.allocate(2102)).isEqualTo("ESIC-2102-00001");
        entityManager.clear();
        assertThat(entityManager.getEntityManager()
                .createNativeQuery("SELECT next_value FROM student_number_sequence WHERE start_year = 2101")
                .getSingleResult())
                .satisfies(v -> assertThat(((Number) v).intValue()).isEqualTo(4));
    }

    @Test
    void refusesWhenTheWidthBoundIsReachedForTheYear() {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO student_number_sequence (start_year, next_value, updated_at) "
                        + "VALUES (2103, 100000, UTC_TIMESTAMP(6))")
                .executeUpdate();
        entityManager.flush();
        assertThatThrownBy(() -> allocator.allocate(2103))
                .isInstanceOf(StudentImportException.class)
                .satisfies(ex -> assertThat(((StudentImportException) ex).kind())
                        .isEqualTo(StudentImportException.Kind.STUDENT_NUMBER_EXHAUSTED));
    }
}
