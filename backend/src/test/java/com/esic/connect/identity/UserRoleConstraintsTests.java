package com.esic.connect.identity;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.esic.connect.shared.config.JpaAuditingConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie l'unicité d'une affectation active (`active_assignment_key`,
 * docs/04 §10.3) tout en garantissant qu'un rôle clôturé reste
 * réattribuable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserRoleConstraintsTests {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void secondActiveAssignmentOfSameUserAndRoleIsRejected() {
        UserAccount user = userAccountRepository.saveAndFlush(newUser());
        Role role = roleRepository.findByCode(RoleCode.TEACHER).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(user, role, Instant.now(), true));

        UserRole duplicate = new UserRole(user, role, Instant.now(), true);
        assertThrows(DataIntegrityViolationException.class,
                () -> userRoleRepository.saveAndFlush(duplicate));
    }

    @Test
    void roleCanBeReassignedAfterPreviousAssignmentIsClosed() {
        UserAccount user = userAccountRepository.saveAndFlush(newUser());
        Role role = roleRepository.findByCode(RoleCode.TEACHER).orElseThrow();

        UserRole firstAssignment = userRoleRepository.saveAndFlush(new UserRole(user, role, Instant.now(), true));
        ReflectionTestUtils.setField(firstAssignment, "active", false);
        ReflectionTestUtils.setField(firstAssignment, "validUntil", Instant.now());
        userRoleRepository.saveAndFlush(firstAssignment);

        UserRole secondAssignment = new UserRole(user, role, Instant.now(), true);
        UserRole saved = userRoleRepository.saveAndFlush(secondAssignment);

        assertThat(saved.getId()).isNotNull();
    }

    private static UserAccount newUser() {
        return new UserAccount("test-" + UUID.randomUUID() + "@esic-connect.test", "Prénom", "Nom",
                AccountStatus.PENDING_ACTIVATION);
    }
}
