import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Lien d'évitement (« Aller au contenu principal »), visible au seul
 * focus clavier.
 *
 * Extrait de {@code AppShell} le 3 septembre 2026 : la coquille
 * n'enveloppe que les routes authentifiées, si bien que les pages
 * publiques (`/login`, `/activation`, `/forbidden`, `/not-found`)
 * portaient un repère `#main-content` sans aucun moyen de l'atteindre au
 * clavier — incohérence relevée par l'audit QA (audit-report.md, finding
 * F-A11Y-1). Ce composant est le point unique de définition ; chaque page
 * qui déclare un `<main id="main-content">` doit le placer juste avant.
 *
 * Exigence : docs/02-cahier-des-charges.md §48 (accessibilité).
 */
@Component({
  selector: 'app-skip-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<a class="skip-link" href="#main-content">Aller au contenu principal</a>`,
  styles: `
    .skip-link {
      position: absolute;
      left: -999px;
      top: 0;
      z-index: 1000;
      padding: 0.5rem 1rem;
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      border-radius: 0 0 4px 0;
    }
    .skip-link:focus {
      left: 0;
    }
  `,
})
export class SkipLink {}
