import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { SessionActivityService } from '../../auth/session-activity.service';

/**
 * Avertissement de fin de session (Lot A).
 *
 * Unique élément véritablement modal de l'application : l'expiration
 * imminente est le seul cas où interrompre la lecture est justifié
 * (docs/02 §17.7 « prévenir clairement l'utilisateur »). Ce n'est PAS un
 * `MatDialog` — la règle « aucune fenêtre modale » du système de design
 * vaut pour les confirmations métier, pas pour une alerte de sécurité
 * bornée dans le temps. Il est construit à la main pour rester
 * accessible : `role="alertdialog"`, `aria-modal`, libellé et description
 * liés, focus déplacé à l'ouverture et rendu à la fermeture, piège de
 * focus, `Échap` = « Continuer » (jamais déconnecter sur `Échap`).
 *
 * Respecte `prefers-reduced-motion` (feuille de style).
 */
@Component({
  selector: 'app-session-timeout-warning',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './session-timeout-warning.html',
  styleUrl: './session-timeout-warning.scss',
})
export class SessionTimeoutWarning {
  private readonly session = inject(SessionActivityService);

  protected readonly visible = this.session.warningVisible;
  protected readonly renewing = this.session.renewing;

  /** Secondes restantes, arrondies, pour l'affichage. */
  protected readonly secondsRemaining = computed(() =>
    Math.max(0, Math.ceil(this.session.msRemaining() / 1000)),
  );

  private readonly continueButton =
    viewChild<ElementRef<HTMLButtonElement>>('continueButton');

  private previousFocus: HTMLElement | null = null;

  constructor() {
    effect(() => {
      if (this.visible()) {
        this.previousFocus = (document.activeElement as HTMLElement) ?? null;
        // Laisse le DOM se rendre avant de déplacer le focus. Re-teste
        // `visible()` : l'avertissement a pu être levé entre-temps (autre
        // onglet, renouvellement), et la vue être détruite.
        queueMicrotask(() => {
          if (this.visible()) {
            this.continueButton()?.nativeElement?.focus();
          }
        });
      } else if (this.previousFocus) {
        this.previousFocus.focus?.();
        this.previousFocus = null;
      }
    });
  }

  protected onContinue(): void {
    this.session.continueSession();
  }

  protected onLogout(): void {
    this.session.endNow();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.onContinue();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    // Piège de focus : deux cibles seulement (Continuer, Se déconnecter).
    const focusables = Array.from(
      (event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>(
        'button:not([disabled])',
      ),
    );
    if (focusables.length === 0) {
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement as HTMLElement | null;
    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
