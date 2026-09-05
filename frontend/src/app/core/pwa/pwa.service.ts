import { Injectable, signal } from '@angular/core';

import { environment } from '../../../environments/environment';

/**
 * Enregistrement du service worker et invite d'installation
 * (EF-PWA-001).
 *
 * <p>L'enregistrement est volontairement <strong>silencieux en cas
 * d'échec</strong> : un navigateur sans service worker — ou une page
 * servie en clair hors `localhost` — doit continuer à fonctionner
 * normalement. La PWA est un supplément, jamais une condition d'accès.
 */
@Injectable({ providedIn: 'root' })
export class PwaService {
  private readonly _installable = signal(false);
  private readonly _registered = signal(false);
  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  /** `true` quand le navigateur a proposé d'installer l'application. */
  readonly installable = this._installable.asReadonly();
  /** `true` quand un service worker contrôle bien la page. */
  readonly registered = this._registered.asReadonly();

  register(): void {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
      return;
    }
    // En développement, le rechargement à chaud et un service worker qui
    // sert la coquille depuis son cache se contrarient : l'enregistrement
    // n'a lieu qu'en production.
    if (!environment.production) {
      return;
    }
    window.addEventListener('beforeinstallprompt', (event) => {
      // Empêcher l'invite native permet de la proposer au bon moment,
      // depuis un bouton explicite, plutôt que par surprise.
      event.preventDefault();
      this.deferredPrompt = event as BeforeInstallPromptEvent;
      this._installable.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this._installable.set(false);
    });
    navigator.serviceWorker
      .register('/sw.js', { scope: '/' })
      .then(() => this._registered.set(true))
      .catch(() => this._registered.set(false));
  }

  /** Affiche l'invite d'installation retenue. */
  async promptInstall(): Promise<void> {
    const prompt = this.deferredPrompt;
    if (!prompt) {
      return;
    }
    this.deferredPrompt = null;
    this._installable.set(false);
    await prompt.prompt();
  }

  /**
   * Vide le cache de données de l'appareil. Appelé à la déconnexion : sur
   * un poste partagé, les données consultables hors ligne ne doivent pas
   * survivre à la session de la personne précédente.
   */
  clearCachedData(): void {
    if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
      return;
    }
    navigator.serviceWorker.controller?.postMessage({ type: 'ESIC_CLEAR_DATA_CACHE' });
  }
}

/** Événement propriétaire de Chromium, absent de la bibliothèque standard. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
}
