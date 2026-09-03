import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { normalizeHttpError } from '../../../core/models/api-error';
import { InvitationsApiService } from '../invitations-api.service';
import { EmailDelivery, InvitationSummary } from '../invitations.models';

/**
 * Suivi des invitations et de leur délivrabilité (EF-USER-007,
 * EF-USER-008 ; docs/02 §11.3).
 *
 * <p>L'écran distingue explicitement deux choses que le cahier interdit
 * de confondre : ce que le produit a fait (« remis au serveur de
 * messagerie ») et ce que le fournisseur a constaté (« délivré »). En
 * développement, Mailpit ne remonte aucun retour : le statut fournisseur
 * reste « inconnu », et l'écran le dit franchement plutôt que d'afficher
 * un « délivré » sans preuve.
 */
@Component({
  selector: 'app-invitation-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './invitation-list.html',
  styleUrl: './invitation-list.scss',
})
export class InvitationList {
  private readonly api = inject(InvitationsApiService);

  protected readonly invitationColumns = ['recipient', 'status', 'expiresAt', 'actions'];
  protected readonly deliveryColumns = [
    'recipientMasked',
    'messageType',
    'internalStatus',
    'providerStatus',
    'attempts',
    'lastAttemptAt',
  ];

  protected readonly invitations = signal<InvitationSummary[]>([]);
  protected readonly deliveries = signal<EmailDelivery[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly infoMessage = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list('PENDING', 0, 50).subscribe({
      next: (page) => {
        this.loading.set(false);
        this.invitations.set(page.content);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(normalizeHttpError(error).message);
      },
    });
    this.api.listDeliveries(undefined, 'ACCOUNT_INVITATION', 0, 50).subscribe({
      next: (page) => this.deliveries.set(page.content),
      error: () => undefined,
    });
  }

  protected resend(invitation: InvitationSummary): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.infoMessage.set(null);
    this.api.resend(invitation.id).subscribe({
      next: () => {
        this.infoMessage.set(
          "Une nouvelle invitation est partie. Le lien précédent ne fonctionne plus.",
        );
        this.reload();
      },
      error: (error: unknown) => {
        this.loading.set(false);
        const normalized = normalizeHttpError(error);
        this.errorMessage.set(
          normalized.status === 409
            ? "Ce compte n'est plus en attente d'activation : la réémission est sans objet."
            : normalized.message,
        );
      },
    });
  }

  /** Libellé lisible du statut interne, sans jargon technique. */
  protected internalLabel(status: string): string {
    switch (status) {
      case 'QUEUED':
        return 'En file';
      case 'SENT_TO_PROVIDER':
        return 'Remis au serveur de messagerie';
      case 'PROCESSING_FAILED':
        return 'Échec du traitement';
      default:
        return status;
    }
  }

  /** Libellé du retour fournisseur ; « inconnu » est une réponse honnête. */
  protected providerLabel(status: string): string {
    switch (status) {
      case 'UNKNOWN':
        return 'Non renseigné par le fournisseur';
      case 'DELIVERED':
        return 'Délivré';
      case 'BOUNCED':
        return 'Rejeté par le destinataire';
      case 'REJECTED':
        return 'Refusé';
      case 'COMPLAINED':
        return 'Signalé comme indésirable';
      default:
        return status;
    }
  }
}
