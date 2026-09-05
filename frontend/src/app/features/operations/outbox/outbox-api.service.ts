import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

/**
 * Un effet de bord de la file transactionnelle (EF-OPS-005). Le
 * **contenu** du message n'est jamais renvoyé par l'API : l'exploitant a
 * besoin de savoir quoi a échoué et pourquoi, pas de lire les données
 * qu'il transporte.
 */
export interface OutboxMessage {
  readonly publicId: string;
  readonly messageType: string;
  readonly status: 'PENDING' | 'SENT' | 'FAILED' | 'DEAD';
  readonly attempts: number;
  readonly lastError: string | null;
  readonly nextAttemptAt: string;
  readonly createdAt: string;
  readonly processedAt: string | null;
}

export interface OutboxPage {
  readonly content: readonly OutboxMessage[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface OutboxSummary {
  readonly pending: number;
  readonly failed: number;
  readonly dead: number;
  readonly sent: number;
}

@Injectable({ providedIn: 'root' })
export class OutboxApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/outbox/messages`;

  list(query: { status?: string | null; page?: number; size?: number }): Observable<OutboxPage> {
    let params = new HttpParams();
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.page !== undefined) {
      params = params.set('page', String(query.page));
    }
    if (query.size !== undefined) {
      params = params.set('size', String(query.size));
    }
    return this.http.get<OutboxPage>(this.base, { params });
  }

  summary(): Observable<OutboxSummary> {
    return this.http.get<OutboxSummary>(`${this.base}/summary`);
  }

  /** `POST /{publicId}/replay` → 204. Refusé (409) hors file d'échec. */
  replay(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${encodeURIComponent(publicId)}/replay`, {});
  }
}
