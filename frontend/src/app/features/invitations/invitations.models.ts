/**
 * Contrats du suivi des invitations et de la délivrabilité
 * (EF-USER-007, EF-USER-008).
 */

export interface InvitationSummary {
  id: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED';
  /**
   * Vrai si l'invitation est encore `PENDING` mais que sa date
   * d'expiration est passée. L'expiration n'est pas un statut stocké :
   * elle se déduit de `expiresAt`.
   */
  expired: boolean;
  expiresAt: string;
  usedAt: string | null;
  createdAt: string;
}

export interface InvitationPage {
  content: InvitationSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Trace d'envoi. Les deux statuts sont volontairement distincts :
 * `internalStatus` dit ce que le produit a fait, `providerStatus` ce que
 * le fournisseur a constaté. « Remis au serveur » n'est pas « délivré ».
 */
export interface EmailDelivery {
  id: string;
  /** Adresse masquée — jamais l'adresse en clair. */
  recipientMasked: string;
  messageType: string;
  internalStatus: 'QUEUED' | 'SENT_TO_PROVIDER' | 'PROCESSING_FAILED';
  providerStatus: 'UNKNOWN' | 'DELIVERED' | 'BOUNCED' | 'REJECTED' | 'COMPLAINED';
  attempts: number;
  lastAttemptAt: string | null;
  lastError: string | null;
  createdAt: string;
}

export interface EmailDeliveryPage {
  content: EmailDelivery[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Ligne du rapport des invitations non activées (EF-REP-010 ;
 * docs/02 §22.3 — « comptes en attente, dernière relance »).
 *
 * `maskedEmail` est volontairement masquée : ce rapport sert à relancer,
 * pas à exporter un annuaire. Corriger une adresse se fait depuis
 * l'écran de suivi des invitations, sous contrôle.
 */
export interface PendingInvitationRow {
  invitationPublicId: string;
  userPublicId: string;
  firstName: string | null;
  lastName: string | null;
  maskedEmail: string;
  lastSentAt: string;
  expiresAt: string;
  expired: boolean;
  daysPending: number;
}

export type PendingInvitationExportFormat = 'csv' | 'xlsx' | 'pdf';

export const PENDING_INVITATION_EXPORT_FORMATS: readonly {
  value: PendingInvitationExportFormat;
  label: string;
}[] = [
  { value: 'csv', label: 'CSV' },
  { value: 'xlsx', label: 'Excel' },
  { value: 'pdf', label: 'PDF' },
];
