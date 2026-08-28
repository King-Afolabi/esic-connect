package com.esic.connect.notification.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * L'écouteur transmet l'événement au mailer ; un échec d'envoi est avalé
 * (l'invitation, déjà committée, doit survivre) sans propager d'exception.
 */
@ExtendWith(MockitoExtension.class)
class InvitationEmailListenerTests {

    @Mock
    private InvitationMailer invitationMailer;

    private AccountInvitationIssuedEvent event() {
        return new AccountInvitationIssuedEvent(1L, UUID.randomUUID(), "cible@esic-connect.test",
                "Cible", "raw-token-value", Instant.now().plusSeconds(3600));
    }

    @Test
    void forwardsInvitationToMailer() {
        AccountInvitationIssuedEvent event = event();

        new InvitationEmailListener(invitationMailer).onAccountInvitationIssued(event);

        verify(invitationMailer).sendActivationInvitation(
                eq("cible@esic-connect.test"), eq("Cible"), eq("raw-token-value"), eq(event.expiresAt()));
    }

    @Test
    void swallowsMailerFailureWithoutPropagating() {
        doThrow(new RuntimeException("smtp indisponible"))
                .when(invitationMailer).sendActivationInvitation(any(), any(), any(), any());

        assertThatCode(() -> new InvitationEmailListener(invitationMailer).onAccountInvitationIssued(event()))
                .doesNotThrowAnyException();
    }
}
