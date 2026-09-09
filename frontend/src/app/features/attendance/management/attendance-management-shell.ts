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

const VIEWS = ['summary', 'sessions', 'classes', 'students', 'justifications'] as const;
type View = (typeof VIEWS)[number];

/**
 * Espace « Suivi d'assiduité » (V10 ; docs/02 §22).
 *
 * <p>Les cinq vues — synthèse, rapports par séance / classe / apprenant,
 * file des justificatifs — partagent un seul titre de page et une
 * <strong>navigation secondaire visible</strong> (`.esic-subnav`).
 * « Synthèse » figure explicitement dans le strip — elle ne disparaît
 * plus quand on la quitte.</p>
 *
 * <p>L'onglet actif est dérivé de l'URL par un signal (et non de
 * `routerLinkActive`) : voir {@link NotificationsShell} — sous détection
 * de changement sans zone, l'état actif d'une coquille dont seul l'enfant
 * change pouvait rester figé.</p>
 *
 * <p>Aucune règle d'autorisation ici : `canActivate` /
 * `canActivateChild` de la route restent alignés sur
 * `AttendanceManagementWeb` et un `PEDAGOGICAL_MANAGER` reste filtré par
 * périmètre côté serveur.</p>
 */
@Component({
  selector: 'app-attendance-management-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './attendance-management-shell.html',
  styleUrl: '../my-attendance/my-attendance-list.scss',
})
export class AttendanceManagementShell {
  private readonly router = inject(Router);
  protected readonly views = VIEWS;

  // Voir {@link NotificationsShell} : on lit `router.url` sur tout
  // évènement de navigation qui se pose (`NavigationEnd` mais aussi
  // `NavigationSkipped` / `NavigationCancel`), une vue enfant qui réécrit
  // ses paramètres de requête au chargement pouvant supplanter la
  // navigation de l'onglet sans émettre `NavigationEnd`.
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

  protected readonly activeView = computed<View>(() => {
    const last = this.url().split(/[?#]/)[0].split('/').filter(Boolean).pop();
    return (VIEWS as readonly string[]).includes(last ?? '') ? (last as View) : 'summary';
  });

  protected readonly label: Record<View, string> = {
    summary: 'Synthèse',
    sessions: 'Par séance',
    classes: 'Par classe',
    students: 'Par apprenant',
    justifications: 'Justificatifs',
  };
}
