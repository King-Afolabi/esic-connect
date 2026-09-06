import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Espace « Notifications » (EF-NOTIF-001, EF-NOTIF-006).
 *
 * <p>Une seule entrée dans la navigation latérale — « Notifications » —
 * ouvre cet espace, qui porte deux vues internes : la liste des
 * notifications et les préférences. Ce sont de <strong>vrais liens de
 * route</strong> (`routerLink` + `routerLinkActive` +
 * `ariaCurrentWhenActive`), jamais une comparaison de préfixe : le lien
 * « Notifications » porte `{ exact: true }` pour ne pas rester actif sur
 * `/notifications/preferences`. Les anciennes URL
 * (`/notifications`, `/notifications/preferences`) restent valides : ce
 * sont les chemins des vues enfants.
 */
@Component({
  selector: 'app-notifications-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './notifications-shell.html',
})
export class NotificationsShell {}
