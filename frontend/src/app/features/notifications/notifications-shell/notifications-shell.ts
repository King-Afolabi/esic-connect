import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationSkipped,
  Router,
  RouterLink,
  RouterOutlet,
} from '@angular/router';
import { filter, map, startWith } from 'rxjs';

/**
 * Espace « Notifications » (EF-NOTIF-001, EF-NOTIF-006).
 *
 * <p>Une seule entrée dans la navigation latérale — « Notifications » —
 * ouvre cet espace, qui porte deux vues internes : la liste des
 * notifications (`centre`) et les préférences. Les anciennes URL
 * (`/notifications`, `/notifications/preferences`) restent valides :
 * `/notifications` redirige vers `centre`.</p>
 *
 * <p>L'onglet actif est <strong>dérivé de l'URL par un signal</strong> et
 * non de `routerLinkActive` : sous détection de changement sans zone,
 * `routerLinkActive` d'une coquille dont seul l'enfant change laissait un
 * <em>état actif résiduel</em> sur la vue précédente. Un `computed` sur
 * l'URL, lui, re-rend toujours une vue OnPush.</p>
 */
@Component({
  selector: 'app-notifications-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './notifications-shell.html',
})
export class NotificationsShell {
  private readonly router = inject(Router);

  // On lit `router.url` (toujours à jour) sur TOUT évènement de navigation
  // qui se pose — `NavigationEnd`, mais aussi `NavigationSkipped` /
  // `NavigationCancel` : une vue enfant qui réécrit ses paramètres de
  // requête au chargement (`NotificationList`) déclenche une navigation
  // « même URL » qui supplante et annule celle de l'onglet, sans jamais
  // émettre `NavigationEnd`. Filtrer le seul `NavigationEnd` laissait
  // alors l'onglet précédent actif (état résiduel).
  private readonly url = toSignal(
    this.router.events.pipe(
      filter(
        (e) =>
          e instanceof NavigationEnd ||
          e instanceof NavigationSkipped ||
          e instanceof NavigationCancel,
      ),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** `'preferences'` sur la vue préférences, `'centre'` partout ailleurs. */
  protected readonly activeTab = computed(() =>
    this.url().split(/[?#]/)[0].includes('/notifications/preferences') ? 'preferences' : 'centre',
  );
}
