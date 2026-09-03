package com.esic.connect.notification.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'écouteur transmet l'événement au mailer et <strong>trace</strong>
 * l'issue de l'envoi (EF-USER-008).
 *
 * <p>Un échec d'envoi est avalé — l'invitation, déjà committée, doit
 * survivre — mais il n'est pas silencieux pour autant : la trace passe en
 * {@code PROCESSING_FAILED}, ce qui est précisément ce qui permet à un
 * responsable de corriger l'adresse et de réémettre.
 */
@ExtendWith(MockitoExtension.class)
class InvitationEmailListenerTests {

    @Mock
    private InvitationMailer invitationMailer;
    @Mock
    private EmailDeliveryService deliveryService;

    private AccountInvitationIssuedEvent event() {
        return new AccountInvitationIssuedEvent(1L, UUID.randomUUID(), "cible@esic-connect.test",
                "Cible", "raw-token-value", Instant.now().plusSeconds(3600));
    }

    private EmailDelivery openedDelivery() {
        EmailDelivery delivery = new EmailDelivery("empreinte", "c…e@e…c.test", null,
                InvitationEmailListener.MESSAGE_TYPE);
        ReflectionTestUtils.setField(delivery, "id", 42L);
        return delivery;
    }

    @Test
    void forwardsInvitationToMailerAndRecordsTheAttempt() {
        AccountInvitationIssuedEvent event = event();
        when(deliveryService.open(any(), any(), any())).thenReturn(openedDelivery());

        new InvitationEmailListener(invitationMailer, deliveryService).onAccountInvitationIssued(event);

        verify(invitationMailer).sendActivationInvitation(
                eq("cible@esic-connect.test"), eq("Cible"), eq("raw-token-value"), eq(event.expiresAt()));
        // « Remis au serveur de messagerie », jamais « délivré ».
        verify(deliveryService).recordSent(42L);
    }

    @Test
    void swallowsMailerFailureWithoutPropagatingButRecordsIt() {
        when(deliveryService.open(any(), any(), any())).thenReturn(openedDelivery());
        doThrow(new RuntimeException("smtp indisponible"))
                .when(invitationMailer).sendActivationInvitation(any(), any(), any(), any());

        assertThatCode(() -> new InvitationEmailListener(invitationMailer, deliveryService)
                .onAccountInvitationIssued(event()))
                .doesNotThrowAnyException();

        // Le motif conservé est une catégorie technique, pas le message.
        verify(deliveryService).recordFailure(anyLong(), contains("RuntimeException"));
    }
}
