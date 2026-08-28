package com.esic.connect.audit;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.esic.connect.shared.config.JpaAuditingConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que l'audit reste lisible après suppression de l'acteur
 * (docs/04 §24.2 : FK `ON DELETE SET NULL`, contrairement au RESTRICT
 * par défaut).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AuditEventTests {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void actorReferenceIsNulledWhenActorIsDeletedButAuditIsKept() {
        UserAccount actor = userAccountRepository.saveAndFlush(new UserAccount(
                "audit-" + UUID.randomUUID() + "@esic-connect.test", "Prénom", "Nom", AccountStatus.ACTIVE));
        String displaySnapshot = actor.getFirstName() + " " + actor.getLastName();

        AuditEvent event = new AuditEvent(Instant.now(), actor.getId(), "LOGIN_SUCCESS", "SECURITY",
                "USER_ACCOUNT", "SUCCESS");
        event.setActorDisplaySnapshot(displaySnapshot);
        Long eventId = auditEventRepository.saveAndFlush(event).getId();

        // Aucune autre table ne référence cet utilisateur (pas de user_role) :
        // la suppression doit réussir grâce au ON DELETE SET NULL.
        userAccountRepository.deleteById(actor.getId());
        userAccountRepository.flush();
        entityManager.clear();

        AuditEvent reloaded = auditEventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getActorUserId()).isNull();
        assertThat(reloaded.getActorDisplaySnapshot()).isEqualTo(displaySnapshot);
    }
}
