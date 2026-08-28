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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Vérifie les contraintes de `user_account` (docs/04 §10.1, §5). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserAccountConstraintsTests {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void emailMustBeUnique() {
        String email = uniqueEmail();
        userAccountRepository.saveAndFlush(newUser(email));

        assertThrows(DataIntegrityViolationException.class,
                () -> userAccountRepository.saveAndFlush(newUser(email)));
    }

    @Test
    void publicIdMustBeUnique() {
        UserAccount first = userAccountRepository.saveAndFlush(newUser(uniqueEmail()));
        UserAccount second = newUser(uniqueEmail());
        // Force volontairement la collision (le @PrePersist ne génère un
        // public_id que si le champ est encore null) pour vérifier la
        // contrainte SQL indépendamment du générateur applicatif.
        ReflectionTestUtils.setField(second, "publicId", first.getPublicId());

        assertThrows(DataIntegrityViolationException.class,
                () -> userAccountRepository.saveAndFlush(second));
    }

    @Test
    void deletingUserReferencedByUserRoleIsRejected() {
        UserAccount user = userAccountRepository.saveAndFlush(newUser(uniqueEmail()));
        Role role = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(user, role, Instant.now(), true));
        Long userId = user.getId();

        // Contexte de persistance vidé pour isoler la vérification de la
        // contrainte SQL (RESTRICT) d'un artefact de cascade Hibernate lié
        // au fait que `UserRole` référence encore `user` en mémoire.
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            userAccountRepository.deleteById(userId);
            userAccountRepository.flush();
        });
    }

    private static UserAccount newUser(String email) {
        return new UserAccount(email, "Prénom", "Nom", AccountStatus.PENDING_ACTIVATION);
    }

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@esic-connect.test";
    }
}
