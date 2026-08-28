package com.esic.connect.identity.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Tests unitaires de l'adaptateur {@link UserAccountUserDetailsService}. */
@ExtendWith(MockitoExtension.class)
class UserAccountUserDetailsServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    private UserAccountUserDetailsService service() {
        return new UserAccountUserDetailsService(userAccountRepository, userRoleRepository);
    }

    @Test
    void normalizesEmailBeforeLookup() {
        UserAccount account = new UserAccount("test@esic-connect.test", "Prénom", "Nom", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 42L);
        when(userAccountRepository.findByEmail("test@esic-connect.test")).thenReturn(Optional.of(account));
        when(userRoleRepository.findByUserId(42L)).thenReturn(List.of());

        UserDetails details = service().loadUserByUsername("  Test@ESIC-Connect.test  ");

        assertThat(details.getUsername()).isEqualTo("test@esic-connect.test");
    }

    @Test
    void throwsWhenAccountUnknown() {
        when(userAccountRepository.findByEmail("unknown@esic-connect.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().loadUserByUsername("unknown@esic-connect.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void onlyActiveRolesBecomeAuthorities() {
        UserAccount account = new UserAccount("teacher@esic-connect.test", "P", "N", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 7L);
        Role teacherRole = new Role(RoleCode.TEACHER, "Formateur", null, true, true);
        Role studentRole = new Role(RoleCode.STUDENT, "Apprenant", null, true, true);
        UserRole activeAssignment = new UserRole(account, teacherRole, Instant.now(), true);
        UserRole closedAssignment = new UserRole(account, studentRole, Instant.now(), false);

        when(userAccountRepository.findByEmail("teacher@esic-connect.test")).thenReturn(Optional.of(account));
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of(activeAssignment, closedAssignment));

        UserDetails details = service().loadUserByUsername("teacher@esic-connect.test");

        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_TEACHER");
    }

    @Test
    void onlyActiveAccountIsEnabled() {
        UserAccount suspended = new UserAccount("suspended@esic-connect.test", "P", "N", AccountStatus.SUSPENDED);
        ReflectionTestUtils.setField(suspended, "id", 9L);
        when(userAccountRepository.findByEmail("suspended@esic-connect.test")).thenReturn(Optional.of(suspended));
        when(userRoleRepository.findByUserId(9L)).thenReturn(List.of());

        UserDetails details = service().loadUserByUsername("suspended@esic-connect.test");

        assertThat(details.isEnabled()).isFalse();
    }
}
