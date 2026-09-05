/**
 * Piste d'audit (EF-AUD-002 ; docs/02 §23). Miroir exact du DTO
 * `com.esic.connect.audit.internal.AuditResponses`.
 *
 * Ne comporte **ni adresse IP, ni colonne JSON brute** : elles ne sont
 * pas dans la table (§23.3) ou ne sortent pas de la base (§23.4).
 */

export interface AuditRow {
  publicId: string;
  occurredAt: string;
  actorDisplay: string | null;
  actorPublicId: string | null;
  actorRole: string | null;
  action: string;
  category: string;
  resourceType: string;
  resourcePublicId: string | null;
  result: string;
  reason: string | null;
  correlationId: string | null;
}

export interface AuditPage {
  content: AuditRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditFacets {
  actions: string[];
  categories: string[];
  resourceTypes: string[];
  results: string[];
}

export interface AuditQuery {
  from: string | null;
  to: string | null;
  action: string | null;
  category: string | null;
  resourceType: string | null;
  result: string | null;
  actor: string | null;
  resource: string | null;
  correlation: string | null;
}

/** Formats d'export, liste fermée alignée sur le serveur. */
export type AuditExportFormat = 'csv' | 'xlsx' | 'pdf';

export const AUDIT_EXPORT_FORMATS: readonly { value: AuditExportFormat; label: string }[] = [
  { value: 'csv', label: 'CSV' },
  { value: 'xlsx', label: 'Excel' },
  { value: 'pdf', label: 'PDF' },
];

/** Rôles autorisés, miroir de `AuditController.AUDIT_ROLES`. */
export const AUDIT_ROLES = ['ADMIN', 'SUPER_ADMIN'] as const;
