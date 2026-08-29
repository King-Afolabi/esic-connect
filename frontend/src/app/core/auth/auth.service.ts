import { HttpClient } from '@angular/common/http';
import { computed, Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, of, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LoginResponse, Session } from '../models/session';
import { Role } from '../models/role';
import { readExpiry, readRoles, readSubject } from './jwt';

/**
 * Point d'entrée unique de l'état d'authentification côté client.
 *
 * Stratégie de stockage du jeton : **en mémoire uniquement** (signal).
 * Ni `localStorage` ni `sessionStorage` ni cookie écrit en JavaScript.
 * Motivation : docs/07-securite-rgpd.md §6 (« aucun token sensible dans
 * localStorage ») et RG-085. La stratégie cible documentée est un cookie
 * `HttpOnly` + refresh token rotatif (docs/03 §15.2, docs/07 §6), non
 * encore exposée par le back-end (seul `POST /api/v1/auth/login`
 * renvoyant un bearer JSON existe aujourd'hui).
 *
 * Conséquence assumée : un rechargement de page perd la session et
 * renvoie l'utilisateur vers la connexion. {@link restoreSession} est le
 * point d'extension où brancher `POST /api/v1/auth/refresh` quand le
 * back-end fournira l'authentification par cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _session = signal<Session | null>(null);

  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session() !== null);
  readonly roles = computed<Role[]>(() => this._session()?.roles ?? []);
  readonly currentUserEmail = computed(() => this._session()?.email ?? null);

  /** Jeton courant pour l'intercepteur, ou `null` si non connecté. */
  get accessToken(): string | null {
    return this._session()?.accessToken ?? null;
  }

  login(email: string, password: string): Observable<Session> {
    const normalizedEmail = email.trim().toLowerCase();
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/v1/auth/login`, {
        email: normalizedEmail,
        password,
      })
      .pipe(
        map((response) => this.toSession(response, normalizedEmail)),
        tap((session) => this._session.set(session)),
      );
  }

  /**
   * Restauration de session après rechargement.
   *
   * Aucune persistance client n'étant autorisée (voir en-tête de classe),
   * il n'y a rien à restaurer aujourd'hui : la méthode complète sans
   * établir de session. Elle est appelée au démarrage via
   * `provideAppInitializer` et constitue le point d'ancrage d'un futur
   * `POST /api/v1/auth/refresh` fondé sur un cookie `HttpOnly`.
   */
  restoreSession(): Observable<void> {
    return of(undefined);
  }

  /** Déconnexion locale : efface la session et retourne à l'écran de connexion. */
  logout(): void {
    // Pas de route `POST /api/v1/auth/logout` côté back-end pour l'instant
    // (jeton stateless, aucune session serveur à révoquer — docs/CURRENT-STATE.md).
    const wasAuthenticated = this._session() !== null;
    this._session.set(null);
    if (wasAuthenticated) {
      void this.router.navigate(['/login']);
    }
  }

  /**
   * Traitement d'une réponse 401 sur un appel authentifié : la session
   * locale est considérée comme expirée ou invalide.
   */
  handleUnauthorized(): void {
    if (this._session() === null) {
      return;
    }
    this._session.set(null);
    void this.router.navigate(['/login'], { queryParams: { reason: 'expired' } });
  }

  hasAnyRole(required: readonly Role[]): boolean {
    if (required.length === 0) {
      return true;
    }
    const held = this.roles();
    return required.some((role) => held.includes(role));
  }

  private toSession(response: LoginResponse, email: string): Session {
    const token = response.accessToken;
    return {
      accessToken: token,
      subject: readSubject(token),
      roles: readRoles(token),
      email,
      expiresAt: readExpiry(token, response.expiresInSeconds),
    };
  }
}
