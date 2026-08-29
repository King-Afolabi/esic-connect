package com.esic.connect.academic.internal;

import com.esic.connect.identity.CurrentUserResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Décision de périmètre pédagogique : accès global déduit des autorités
 * Spring Security ({@code ROLE_ADMIN} / {@code ROLE_SUPER_ADMIN} /
 * {@code ROLE_SCHOOL_ADMINISTRATION}), sinon filtrage sur les formations
 * du périmètre effectif au jour donné par l'<em>horloge injectée</em>.
 */
@ExtendWith(MockitoExtension.class)
class AcademicScopeGuardTests {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final String SUBJECT = "11111111-1111-1111-1111-111111111111";

    @Mock
    private PedagogicalAssignmentRepository assignmentRepository;
    @Mock
    private CurrentUserResolver currentUserResolver;

    private AcademicScopeGuard guard() {
        return new AcademicScopeGuard(assignmentRepository, currentUserResolver, FIXED_CLOCK);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(SUBJECT, "n/a", authorities));
    }

    @Test
    void adminHasGlobalScopeAndNoFiltering() {
        authenticate("ROLE_TEACHER", "ROLE_ADMIN");
        AcademicScopeGuard guard = guard();

        assertThat(guard.hasGlobalScope()).isTrue();
        assertThat(guard.visibleProgramIds()).isNull();
        guard.requireProgramInScope(null); // ne lève pas
        verifyNoInteractions(assignmentRepository, currentUserResolver);
    }

    @Test
    void schoolAdministrationHasGlobalScope() {
        authenticate("ROLE_SCHOOL_ADMINISTRATION");
        assertThat(guard().hasGlobalScope()).isTrue();
    }

    @Test
    void managerPlusTeacherIsScopedAndUsesInjectedClockDate() {
        authenticate("ROLE_PEDAGOGICAL_MANAGER", "ROLE_TEACHER");
        when(currentUserResolver.resolveInternalId(SUBJECT)).thenReturn(Optional.of(7L));
        when(assignmentRepository.findScopedProgramIds(7L, PedagogicalAssignmentStatus.ACTIVE, TODAY))
                .thenReturn(List.of(3L, 4L));

        assertThat(guard().hasGlobalScope()).isFalse();
        assertThat(guard().visibleProgramIds()).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void scopedCallerWithoutResolvableIdSeesNothing() {
        authenticate("ROLE_PEDAGOGICAL_MANAGER");
        when(currentUserResolver.resolveInternalId(SUBJECT)).thenReturn(Optional.empty());

        assertThat(guard().visibleProgramIds()).isEmpty();
    }

    @Test
    void requireProgramInScopeUsesInjectedClockDateAndRejectsWhenOutOfScope() {
        authenticate("ROLE_PEDAGOGICAL_MANAGER");
        Program program = new Program("PRG", "P", ProgramType.BTS, null);
        org.springframework.test.util.ReflectionTestUtils.setField(program, "id", 42L);
        when(currentUserResolver.resolveInternalId(SUBJECT)).thenReturn(Optional.of(7L));
        when(assignmentRepository.existsEffectiveScope(42L, 7L, PedagogicalAssignmentStatus.ACTIVE, TODAY))
                .thenReturn(false);

        assertThatThrownBy(() -> guard().requireProgramInScope(program))
                .isInstanceOf(AcademicException.class)
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.OUT_OF_SCOPE);
    }

    @Test
    void requireProgramInScopePassesWhenAssignmentCoversInjectedClockDate() {
        authenticate("ROLE_PEDAGOGICAL_MANAGER");
        Program program = new Program("PRG", "P", ProgramType.BTS, null);
        org.springframework.test.util.ReflectionTestUtils.setField(program, "id", 42L);
        when(currentUserResolver.resolveInternalId(SUBJECT)).thenReturn(Optional.of(7L));
        when(assignmentRepository.existsEffectiveScope(42L, 7L, PedagogicalAssignmentStatus.ACTIVE, TODAY))
                .thenReturn(true);

        guard().requireProgramInScope(program); // ne lève pas
    }

    @Test
    void anonymousContextIsNotGlobal() {
        lenient().when(currentUserResolver.resolveInternalId(SUBJECT)).thenReturn(Optional.of(7L));
        assertThat(guard().hasGlobalScope()).isFalse();
        assertThat(guard().visibleProgramIds()).isEmpty();
    }
}
