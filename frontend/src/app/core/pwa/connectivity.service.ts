import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/**
 * État de connexion du navigateur (EF-PWA-002).
 *
 * `navigator.onLine` ne dit pas si le serveur répond : il dit seulement
 * que l'appareil a une interface réseau active. C'est un indice utile —
 * il bascule instantanément quand le Wi-Fi tombe — mais insuffisant. Les
 * appels qui échouent avec un statut `0` sont donc la seconde source :
 * l'intercepteur d'erreurs signale une panne réseau à ce service, qui
 * bascule l'interface en mode hors ligne même si le système se croit
 * connecté.
 */
@Injectable({ providedIn: 'root' })
export class ConnectivityService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly _online = signal(this.readNavigatorState());

  /** `true` tant qu'aucune raison de croire le réseau indisponible. */
  readonly online = this._online.asReadonly();

  constructor() {
    if (typeof window === 'undefined') {
      return;
    }
    const goOnline = () => this._online.set(true);
    const goOffline = () => this._online.set(false);
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    this.destroyRef.onDestroy(() => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
    });
  }

  /** Signalé par l'intercepteur HTTP sur une erreur de statut `0`. */
  reportNetworkFailure(): void {
    this._online.set(false);
  }

  /** Signalé dès qu'une réponse serveur arrive : le réseau est bien là. */
  reportNetworkSuccess(): void {
    this._online.set(true);
  }

  private readNavigatorState(): boolean {
    return typeof navigator === 'undefined' || navigator.onLine !== false;
  }
}
