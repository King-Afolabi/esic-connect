import { HttpClient } from '@angular/common/http';
import { computed, Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, from, map, of, shareReplay, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CaptchaConfig,
  MfaEnrollment,
  MfaStatus,
  PasskeyCredential,
  TrustedDevice,
} from '../models/mfa';
import { LoginOutcome, LoginResponse, Session } from '../models/session';
import { Role } from '../models/role';
import { DeviceIdService } from './device-id.service';
import { readExpiry, readRoles, readSubject } from './jwt';
import { base64UrlToBytes, bytesToBase64Url } from './webauthn';

/**
 * Point d'entrée unique de l'état d'authentification côté client.
 *
 * Stratégie de stockage du jeton d'accès : **en mémoire uniquement**
 * (signal). Ni `localStorage` ni `sessionStorage` ni cookie écrit en
 * JavaScript. Motivation : docs/08-securite-rgpd.md §6 (« aucun token
 * sensible dans localStorage ») et RG-093.
 *
 * La continuité de session au rechargement repose sur un **cookie de
 * renouvellement `HttpOnly` rotatif** posé par le back-end
 * (docs/02 §17.7). {@link restoreSession}, appelée au démarrage, échange
 * ce cookie contre un nouveau jeton d'accès via `POST /api/v1/auth/refresh`
 * puis récupère l'identité via `GET /api/v1/auth/me`. En l'absence de
 * cookie valide (jamais connecté, session expirée, déconnexion), elle se
 * termine sans session : l'utilisateur voit l'écran de connexion.
 *
 * {@link refreshSession} rejoue le même échange en cours de session,
 * lorsqu'un appel métier revient en `401` parce que le jeton d'accès —
 * de courte durée — a expiré. L'appel est en **file unique** : plusieurs
 * `401` concurrents partagent un seul renouvellement.
 *
 * Tous les appels d'authentification portent `withCredentials: true` :
 * indispensable pour qu'un déploiement multi-origine transmette le
 * cookie ; sans effet en mono-origine (proxy `ng serve`, reverse proxy
 * de production).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly deviceId = inject(DeviceIdService);

  private readonly _session = signal<Session | null>(null);

  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session() !== null);
  readonly roles = computed<Role[]>(() => this._session()?.roles ?? []);
  readonly currentUserEmail = computed(() => this._session()?.email ?? null);

  /** Jeton courant pour l'intercepteur, ou `null` si non connecté. */
  get accessToken(): string | null {
    return this._session()?.accessToken ?? null;
  }

  /**
   * Connexion par mot de passe.
   *
   * Deux issues, que l'appelant doit distinguer : la session est ouverte,
   * ou un second facteur est exigé (RG-007, AC-021). Le jeton n'est posé
   * dans l'état que dans le premier cas.
   *
   * @param captchaToken jeton anti-robot, s'il a été produit par le
   *                     widget ; le serveur ne l'exige qu'après des
   *                     échecs répétés (AC-022)
   */
  login(email: string, password: string, captchaToken?: string | null): Observable<LoginOutcome> {
    const normalizedEmail = email.trim().toLowerCase();
    return this.http
      .post<LoginResponse>(
        `${environment.apiBaseUrl}/v1/auth/login`,
        { email: normalizedEmail, password, captchaToken: captchaToken ?? null },
        { headers: this.deviceHeaders(), withCredentials: true },
      )
      .pipe(map((response) => this.toOutcome(response, normalizedEmail)));
  }

  /** Résout un défi par un code TOTP ou un code de récupération. */
  verifyMfa(challengeId: string, code: string, email: string): Observable<Session> {
    return this.http
      .post<LoginResponse>(
        `${environment.apiBaseUrl}/v1/auth/mfa/verify`,
        { challengeId, code },
        { headers: this.deviceHeaders(), withCredentials: true },
      )
      .pipe(map((response) => this.establish(response, email)));
  }

  /**
   * Ouvre un enrôlement de second facteur. `challengeId` n'est fourni que
   * pendant une connexion bloquée ; depuis une session ouverte, le jeton
   * porteur suffit.
   */
  startMfaEnrollment(challengeId?: string): Observable<MfaEnrollment> {
    return this.http.post<MfaEnrollment>(
      `${environment.apiBaseUrl}/v1/auth/mfa/enroll`,
      challengeId ? { challengeId } : {},
      { headers: this.deviceHeaders() },
    );
  }

  /**
   * Confirme l'enrôlement. Lorsqu'il venait d'une connexion bloquée, la
   * réponse porte la session : l'utilisateur n'a pas à ressaisir son mot
   * de passe.
   */
  confirmMfaEnrollment(
    code: string,
    challengeId: string | undefined,
    email: string,
  ): Observable<{ recoveryCodes: string[]; session: Session | null }> {
    return this.http
      .post<{ recoveryCodes: string[]; session?: LoginResponse }>(
        `${environment.apiBaseUrl}/v1/auth/mfa/enroll/confirm`,
        challengeId ? { challengeId, code } : { code },
        { headers: this.deviceHeaders(), withCredentials: true },
      )
      .pipe(
        map((response) => ({
          recoveryCodes: response.recoveryCodes,
          session: response.session ? this.establish(response.session, email) : null,
        })),
      );
  }

  mfaStatus(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>(`${environment.apiBaseUrl}/v1/auth/mfa`);
  }

  regenerateRecoveryCodes(code: string): Observable<string[]> {
    return this.http
      .post<{ recoveryCodes: string[] }>(`${environment.apiBaseUrl}/v1/auth/mfa/recovery-codes`, {
        code,
      })
      .pipe(map((response) => response.recoveryCodes));
  }

  disableMfa(code: string): Observable<void> {
    return this.http.request<void>('DELETE', `${environment.apiBaseUrl}/v1/auth/mfa`, {
      body: { code },
    });
  }

  /** Configuration publique du widget anti-robot (EF-AUTH-011). */
  captchaConfig(): Observable<CaptchaConfig> {
    return this.http.get<CaptchaConfig>(`${environment.apiBaseUrl}/v1/auth/captcha`);
  }

  // ------------------------------------------------------------------
  // Clés d'accès (EF-AUTH-006, EF-AUTH-007)
  // ------------------------------------------------------------------

  passkeys(): Observable<PasskeyCredential[]> {
    return this.http.get<PasskeyCredential[]>(
      `${environment.apiBaseUrl}/v1/auth/webauthn/credentials`,
    );
  }

  revokePasskey(id: string): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiBaseUrl}/v1/auth/webauthn/credentials/${id}`,
    );
  }

  /**
   * Enregistre une clé d'accès sur cet appareil.
   *
   * Le navigateur vérifie l'utilisateur localement — empreinte, visage ou
   * code — et ne renvoie qu'une clé publique et une signature : aucune
   * donnée biométrique n'atteint le serveur (RG-091, AC-020).
   */
  registerPasskey(label: string): Observable<PasskeyCredential> {
    return this.http
      .post<PasskeyRegistrationOptions>(
        `${environment.apiBaseUrl}/v1/auth/webauthn/register/options`,
        {},
      )
      .pipe(
        switchMap((options) => from(this.createCredential(options, label))),
        switchMap((body) =>
          this.http.post<PasskeyCredential>(
            `${environment.apiBaseUrl}/v1/auth/webauthn/register`,
            body,
          ),
        ),
      );
  }

  /** Connexion sans mot de passe par clé d'accès. */
  loginWithPasskey(): Observable<Session> {
    return this.http
      .post<PasskeyAuthenticationOptions>(
        `${environment.apiBaseUrl}/v1/auth/webauthn/login/options`,
        {},
      )
      .pipe(
        switchMap((options) => from(this.requestAssertion(options))),
        switchMap((body) =>
          this.http.post<LoginResponse>(`${environment.apiBaseUrl}/v1/auth/webauthn/login`, body, {
            headers: this.deviceHeaders(),
            withCredentials: true,
          }),
        ),
        map((response) => this.establish(response, this.currentUserEmail() ?? '')),
      );
  }

  // ------------------------------------------------------------------
  // Appareils de confiance (EF-AUTH-013)
  // ------------------------------------------------------------------

  trustedDevices(): Observable<TrustedDevice[]> {
    return this.http.get<TrustedDevice[]>(`${environment.apiBaseUrl}/v1/auth/devices`);
  }

  revokeTrustedDevice(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/v1/auth/devices/${id}`);
  }

  /** Renouvellement en cours, partagé tant qu'il n'est pas terminé (file unique). */
  private refreshInFlight: Observable<boolean> | null = null;

  /**
   * Restauration de session après rechargement.
   *
   * Échange le cookie de renouvellement `HttpOnly` contre un jeton
   * d'accès (`POST /api/v1/auth/refresh`), puis lit l'identité du compte
   * (`GET /api/v1/auth/me`) — le jeton seul ne porte pas l'adresse. En
   * l'absence de cookie valide, la méthode se termine sans session, sans
   * erreur ni redirection : c'est un démarrage anonyme normal.
   *
   * Appelée une fois au démarrage via `provideAppInitializer`.
   */
  restoreSession(): Observable<void> {
    return this.http
      .post<LoginResponse>(
        `${environment.apiBaseUrl}/v1/auth/refresh`,
        {},
        { headers: this.deviceHeaders(), withCredentials: true },
      )
      .pipe(
        switchMap((response) => {
          if (!response.accessToken) {
            return of(undefined);
          }
          const token = response.accessToken;
          return this.http
            .get<{ email: string }>(`${environment.apiBaseUrl}/v1/auth/me`, {
              headers: { Authorization: `Bearer ${token}` },
            })
            .pipe(
              map((me) => {
                this._session.set(this.toSession(response, me.email));
                return undefined;
              }),
            );
        }),
        catchError(() => {
          this._session.set(null);
          return of(undefined);
        }),
      );
  }

  /**
   * Renouvelle le jeton d'accès en cours de session, à partir du cookie
   * `HttpOnly`. Sert à l'intercepteur d'erreurs : quand un appel métier
   * revient en `401`, le jeton d'accès — de courte durée — a expiré, mais
   * la session de renouvellement peut être encore valide.
   *
   * File unique : plusieurs `401` concurrents s'abonnent au même appel.
   * Résout `true` si un nouveau jeton est en place, `false` sinon. En cas
   * d'échec, la session locale n'est **pas** purgée ici : l'appelant
   * (l'intercepteur) décide, via {@link handleUnauthorized}, de renvoyer
   * l'utilisateur vers la connexion.
   */
  refreshSession(): Observable<boolean> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    const currentEmail = this._session()?.email ?? '';
    this.refreshInFlight = this.http
      .post<LoginResponse>(
        `${environment.apiBaseUrl}/v1/auth/refresh`,
        {},
        { headers: this.deviceHeaders(), withCredentials: true },
      )
      .pipe(
        map((response) => {
          if (!response.accessToken) {
            return false;
          }
          this._session.set(this.toSession(response, currentEmail));
          return true;
        }),
        catchError(() => of(false)),
        finalize(() => {
          this.refreshInFlight = null;
        }),
        shareReplay(1),
      );
    return this.refreshInFlight;
  }

  /**
   * Déconnexion.
   *
   * Appelle `POST /api/v1/auth/logout`, qui inscrit le jeton courant sur
   * la liste de refus du serveur jusqu'à son expiration (EF-AUTH-014) :
   * sans cet appel, un jeton copié resterait utilisable après la
   * « déconnexion ».
   *
   * La session locale est effacée **quoi qu'il arrive**, y compris si
   * l'appel échoue : refuser de déconnecter l'utilisateur parce que le
   * serveur ne répond pas serait le pire des deux mondes. L'échec est
   * silencieux côté interface ; le jeton expirera de lui-même.
   */
  logout(): void {
    const wasAuthenticated = this._session() !== null;
    if (wasAuthenticated) {
      this.http
        .post<void>(`${environment.apiBaseUrl}/v1/auth/logout`, {}, { withCredentials: true })
        .subscribe({ next: () => undefined, error: () => undefined });
    }
    this._session.set(null);
    if (wasAuthenticated) {
      void this.router.navigate(['/login']);
    }
  }

  /**
   * Demande un lien de réinitialisation.
   *
   * Le serveur répond de façon identique que l'adresse existe ou non
   * (docs/02 §17.8) : l'interface ne doit donc jamais afficher de message
   * différencié, ni tenter d'en déduire quoi que ce soit.
   */
  requestPasswordReset(email: string): Observable<void> {
    return this.http
      .post<{ message: string }>(`${environment.apiBaseUrl}/v1/auth/forgot-password`, {
        email: email.trim().toLowerCase(),
      })
      .pipe(map(() => undefined));
  }

  /** Consomme un jeton de réinitialisation et définit le nouveau mot de passe. */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${environment.apiBaseUrl}/v1/auth/reset-password`, {
      token,
      newPassword,
    });
  }

  /**
   * Change le mot de passe de l'utilisateur connecté (EF-AUTH,
   * docs/02 §17.1). Le serveur vérifie le mot de passe actuel, applique
   * la politique, puis ferme **toutes** les sessions du compte et vide le
   * cookie de renouvellement — `withCredentials` est donc indispensable.
   * Le compte visé est le sujet du jeton : aucun identifiant n'est
   * transmis, on ne peut pas viser un autre compte.
   */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(
      `${environment.apiBaseUrl}/v1/auth/change-password`,
      { currentPassword, newPassword },
      { withCredentials: true },
    );
  }

  /**
   * Termine la session locale après un changement de mot de passe réussi
   * et renvoie vers la connexion avec le bandeau dédié. Le serveur a
   * déjà invalidé toutes les sessions et vidé le cookie ; il ne reste
   * qu'à oublier le jeton en mémoire.
   */
  completePasswordChange(): void {
    this._session.set(null);
    void this.router.navigate(['/login'], { queryParams: { reason: 'password-changed' } });
  }

  /**
   * Traitement d'une réponse 401 sur un appel authentifié : la session
   * locale est considérée comme expirée ou invalide.
   */
  handleUnauthorized(): void {
    this.expireSession();
  }

  /**
   * Termine la session locale et renvoie vers la connexion en
   * **préservant la route de retour** (`?redirect=`), sauf si l'on est
   * déjà sur `/login`. Sert aussi bien au parcours réactif (401 sur un
   * appel métier) qu'au parcours proactif (expiration d'inactivité,
   * plafond absolu — voir {@link SessionActivityService}).
   *
   * La préservation de la route permet à l'utilisateur de reprendre là
   * où il était après s'être reconnecté (Lot A / Lot G).
   */
  expireSession(): void {
    if (this._session() === null) {
      return;
    }
    this._session.set(null);
    const current = this.router.url;
    const preserved =
      current && current !== '/' && !current.startsWith('/login') ? current : undefined;
    void this.router.navigate(['/login'], {
      queryParams: { reason: 'expired', ...(preserved ? { redirect: preserved } : {}) },
    });
  }

  hasAnyRole(required: readonly Role[]): boolean {
    if (required.length === 0) {
      return true;
    }
    const held = this.roles();
    return required.some((role) => held.includes(role));
  }

  private toOutcome(response: LoginResponse, email: string): LoginOutcome {
    if (response.mfa) {
      // Aucun jeton n'est posé : la connexion n'est pas terminée.
      return { kind: 'challenge', challenge: response.mfa, email };
    }
    return { kind: 'session', session: this.establish(response, email) };
  }

  /** Transforme une réponse porteuse de jeton en session, et l'installe. */
  private establish(response: LoginResponse, email: string): Session {
    const session = this.toSession(response, email);
    this._session.set(session);
    return session;
  }

  private toSession(response: LoginResponse, email: string): Session {
    const token = response.accessToken;
    if (!token) {
      throw new Error("La réponse ne contient pas de jeton d'accès.");
    }
    return {
      accessToken: token,
      subject: readSubject(token),
      roles: readRoles(token),
      email,
      expiresAt: readExpiry(token, response.expiresInSeconds ?? 0),
    };
  }

  /**
   * En-tête d'appareil, omis quand le stockage local est indisponible :
   * le serveur traite alors la connexion comme venant d'un appareil
   * inconnu, ce qui est le comportement sûr.
   */
  private deviceHeaders(): Record<string, string> {
    const id = this.deviceId.current();
    return id ? { 'X-Device-Id': id } : {};
  }

  private async createCredential(
    options: PasskeyRegistrationOptions,
    label: string,
  ): Promise<unknown> {
    const credential = (await navigator.credentials.create({
      publicKey: {
        challenge: base64UrlToBytes(options.challenge),
        rp: { id: options.rp.id, name: options.rp.name },
        user: {
          id: base64UrlToBytes(options.user.id),
          name: options.user.name,
          displayName: options.user.displayName,
        },
        pubKeyCredParams: options.pubKeyCredParams.map((parameter) => ({
          type: 'public-key' as const,
          alg: parameter.alg,
        })),
        excludeCredentials: options.excludeCredentials.map((descriptor) => ({
          type: 'public-key' as const,
          id: base64UrlToBytes(descriptor.id),
        })),
        authenticatorSelection: {
          residentKey: options.authenticatorSelection.residentKey as ResidentKeyRequirement,
          userVerification: options.authenticatorSelection
            .userVerification as UserVerificationRequirement,
        },
        timeout: options.timeout,
        attestation: 'none',
      },
    })) as PublicKeyCredential | null;
    if (!credential) {
      throw new Error("L'appareil n'a produit aucune clé d'accès.");
    }
    const attestation = credential.response as AuthenticatorAttestationResponse;
    return {
      id: credential.id,
      label,
      transports:
        typeof attestation.getTransports === 'function' ? attestation.getTransports() : [],
      response: {
        clientDataJSON: bytesToBase64Url(attestation.clientDataJSON),
        attestationObject: bytesToBase64Url(attestation.attestationObject),
      },
    };
  }

  private async requestAssertion(options: PasskeyAuthenticationOptions): Promise<unknown> {
    const credential = (await navigator.credentials.get({
      publicKey: {
        challenge: base64UrlToBytes(options.challenge),
        rpId: options.rpId,
        timeout: options.timeout,
        userVerification: options.userVerification as UserVerificationRequirement,
        allowCredentials: options.allowCredentials.map((descriptor) => ({
          type: 'public-key' as const,
          id: base64UrlToBytes(descriptor.id),
        })),
      },
    })) as PublicKeyCredential | null;
    if (!credential) {
      throw new Error("Aucune clé d'accès n'a été présentée.");
    }
    const assertion = credential.response as AuthenticatorAssertionResponse;
    return {
      challengeId: options.challengeId,
      id: credential.id,
      response: {
        clientDataJSON: bytesToBase64Url(assertion.clientDataJSON),
        authenticatorData: bytesToBase64Url(assertion.authenticatorData),
        signature: bytesToBase64Url(assertion.signature),
        userHandle: assertion.userHandle ? bytesToBase64Url(assertion.userHandle) : null,
      },
    };
  }
}

/** Options d'enregistrement, telles que produites par `WebAuthnWeb`. */
interface PasskeyRegistrationOptions {
  challenge: string;
  rp: { id: string; name: string };
  user: { id: string; name: string; displayName: string };
  pubKeyCredParams: { type: string; alg: number }[];
  excludeCredentials: { type: string; id: string }[];
  authenticatorSelection: { residentKey: string; userVerification: string };
  timeout: number;
  attestation: string;
}

/** Options d'assertion, telles que produites par `WebAuthnWeb`. */
interface PasskeyAuthenticationOptions {
  challengeId: string;
  challenge: string;
  rpId: string;
  timeout: number;
  userVerification: string;
  allowCredentials: { type: string; id: string }[];
}
