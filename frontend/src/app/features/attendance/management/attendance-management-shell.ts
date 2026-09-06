import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Espace « Suivi d'assiduité » (V10 ; docs/02 §22).
 *
 * <p>Les cinq vues — synthèse, rapports par séance / classe / apprenant,
 * file des justificatifs — partagent un seul titre de page et une
 * <strong>navigation secondaire visible</strong> (`.esic-subnav`). Ce
 * sont de vrais liens de route : `routerLinkActive` pose l'état actif et
 * `ariaCurrentWhenActive` l'expose aux technologies d'assistance. La vue
 * « Synthèse » figure explicitement dans le strip — elle ne disparaît
 * plus quand on la quitte.</p>
 *
 * <p>Aucune règle d'autorisation ici : `canActivate` / `canActivateChild`
 * de la route restent alignés sur `AttendanceManagementWeb`, et un
 * `PEDAGOGICAL_MANAGER` reste filtré par périmètre côté serveur.</p>
 */
@Component({
  selector: 'app-attendance-management-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './attendance-management-shell.html',
  styleUrl: '../my-attendance/my-attendance-list.scss',
})
export class AttendanceManagementShell {}
