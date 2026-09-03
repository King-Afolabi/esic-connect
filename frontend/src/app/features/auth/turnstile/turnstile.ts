import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { AuthService } from '../../../core/auth/auth.service';

/**
 * Widget anti-robot Cloudflare Turnstile (EF-AUTH-011, docs/02 §17.9).
 *
 * <p><strong>Ce composant ne protège rien à lui seul.</strong> La
 * vérification qui compte est faite par le serveur, qui appelle
 * Cloudflare avec sa clé secrète. Le widget ne fait que produire un jeton
 * à transmettre. Un formulaire dont le seul contrôle serait ici ne serait
 * pas protégé : il suffirait de ne pas exécuter ce code.
 *
 * <p>Le composant interroge d'abord `GET /auth/captcha`. Si aucun
 * fournisseur n'est configuré — cas du développement local — il ne rend
 * rien du tout : mieux vaut une absence franche qu'un ornement laissant
 * croire à une protection inexistante.
 *
 * <p>Le script est chargé depuis Cloudflare ; la politique de sécurité de
 * contenu doit l'autoriser pour que le widget s'affiche.
 */
@Component({
  selector: 'app-turnstile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (enforced()) {
      <div #host class="turnstile" data-testid="turnstile-host"></div>
    }
  `,
  styles: `
    .turnstile {
      margin-block: 0.5rem;
      min-height: 65px;
    }
  `,
})
export class Turnstile {
  private static readonly SCRIPT_URL =
    'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  /** Jeton produit par le widget, ou `null` s'il a expiré. */
  readonly token = output<string | null>();

  protected readonly enforced = signal(false);
  private readonly host = viewChild<ElementRef<HTMLElement>>('host');

  private siteKey = '';
  private rendered = false;

  constructor() {
    this.auth.captchaConfig().subscribe({
      next: (config) => {
        if (!config.enforced || !config.siteKey) {
          return;
        }
        this.siteKey = config.siteKey;
        this.enforced.set(true);
        void this.load();
      },
      // Une configuration illisible ne doit pas bloquer le formulaire :
      // le serveur reste l'autorité et refusera si nécessaire.
      error: () => undefined,
    });
    this.destroyRef.onDestroy(() => this.token.emit(null));
  }

  private async load(): Promise<void> {
    await this.ensureScript();
    // Le conteneur n'existe qu'après le rendu conditionnel : on attend un
    // tour de boucle pour que la vue soit à jour.
    await Promise.resolve();
    const element = this.host()?.nativeElement;
    const api = (window as unknown as { turnstile?: TurnstileApi }).turnstile;
    if (!element || !api || this.rendered) {
      return;
    }
    this.rendered = true;
    api.render(element, {
      sitekey: this.siteKey,
      callback: (token: string) => this.token.emit(token),
      'expired-callback': () => this.token.emit(null),
      'error-callback': () => this.token.emit(null),
    });
  }

  private ensureScript(): Promise<void> {
    const base = Turnstile.SCRIPT_URL.split('?')[0];
    if (document.querySelector(`script[src^="${base}"]`)) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      const script = document.createElement('script');
      script.src = Turnstile.SCRIPT_URL;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      // Cloudflare injoignable : on n'empêche pas l'utilisateur de
      // continuer. Le serveur applique sa propre politique de repli
      // (DEC-S2-004).
      script.onerror = () => resolve();
      document.head.appendChild(script);
    });
  }
}

interface TurnstileApi {
  render(
    container: HTMLElement,
    options: {
      sitekey: string;
      callback: (token: string) => void;
      'expired-callback': () => void;
      'error-callback': () => void;
    },
  ): void;
}
