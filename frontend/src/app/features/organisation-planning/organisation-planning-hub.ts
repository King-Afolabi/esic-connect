import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../core/auth/role-context.service';
import { Role } from '../../core/models/role';

interface HubSection {
  readonly label: string;
  readonly path: string;
  readonly icon: string;
  readonly summary: string;
  readonly roles: readonly Role[];
}

/**
 * Périmètre de chaque sous-section, repris **à l'identique** des routes
 * regroupées ici (`app.routes.ts`) — elles-mêmes alignées sur le
 * `@PreAuthorize` de leur contrôleur. Le masquage frontend ne fait que
 * refléter ce périmètre : Spring Security reste l'autorité, et un
 * `PEDAGOGICAL_MANAGER` reste filtré par périmètre pédagogique côté
 * serveur.
 */
const HUB_SECTIONS: readonly HubSection[] = [
  {
    label: 'Référentiels académiques',
    path: '/academic',
    icon: 'school',
    summary: 'Années scolaires, formations, niveaux, promotions et classes.',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    label: 'Organisation physique',
    path: '/organization',
    icon: 'apartment',
    summary: 'Sites, bâtiments, salles, QR fixe de salle et plages réseau.',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    label: 'Planning',
    path: '/planning',
    icon: 'calendar_month',
    summary: 'Import, construction au calendrier, versions et publication.',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    label: 'Alternance',
    path: '/alternation',
    icon: 'sync_alt',
    summary: 'Rythmes, affectations aux classes et exceptions.',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
];

/**
 * Point d'entrée « Organisation &amp; planning » : une seule entrée dans
 * la navigation latérale, qui rassemble quatre sous-sections auparavant
 * séparées. Aucune fonctionnalité retirée, aucune route cassée — les
 * chemins `/academic`, `/organization`, `/planning`, `/alternation`
 * restent valides et `activeNavPath` garde cette entrée active dessus
 * (`matchPaths`).
 */
@Component({
  selector: 'app-organisation-planning-hub',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatIconModule],
  templateUrl: './organisation-planning-hub.html',
  styleUrl: './organisation-planning-hub.scss',
})
export class OrganisationPlanningHub {
  private readonly roleContext = inject(RoleContextService);

  protected readonly sections = computed<readonly HubSection[]>(() => {
    const held = this.roleContext.effectiveRoles();
    return HUB_SECTIONS.filter((section) => section.roles.some((r) => held.includes(r)));
  });
}
