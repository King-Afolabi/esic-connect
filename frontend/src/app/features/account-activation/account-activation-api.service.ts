import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ActivateAccountRequest, InvitationValidation } from './account-activation.models';

/**
 * Accès aux deux endpoints **publics** d'activation de compte
 * (`AccountInvitationController`). Le jeton d'invitation n'est jamais
 * journalisé et n'est transmis que dans le paramètre / champ attendu par
 * le back-end. Ce service ne dépend d'aucun état d'authentification.
 */
@Injectable({ providedIn: 'root' })
export class AccountActivationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/account-invitations`;

  /**
   * `GET …/validate?token=<jeton>` — réponse toujours `200` avec
   * `{ valid: boolean }` (aucune donnée personnelle, aucun motif).
   */
  validate(token: string): Observable<InvitationValidation> {
    return this.http.get<InvitationValidation>(`${this.baseUrl}/validate`, {
      params: { token },
    });
  }

  /**
   * `POST …/activate` avec `{ token, password }` — succès `204 No Content`
   * (aucun identifiant de session renvoyé).
   */
  activate(request: ActivateAccountRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/activate`, request);
  }
}
