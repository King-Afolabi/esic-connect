import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { RoleContextService, ROLE_CONTEXT_LABELS } from '../../auth/role-context.service';
import { Role } from '../../models/role';

/**
 * Sélecteur de contexte d'utilisation (docs/02-cahier-des-charges.md §6.1).
 *
 * Ne s'affiche que si le compte cumule au moins deux rôles. Le choix ne
 * pilote que l'affichage et la navigation : voir {@link RoleContextService}.
 */
@Component({
  selector: 'app-role-context-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './role-context-menu.html',
  styleUrl: './role-context-menu.scss',
})
export class RoleContextMenu {
  private readonly context = inject(RoleContextService);

  protected readonly contextLabel = (role: Role): string => ROLE_CONTEXT_LABELS[role];

  protected readonly hasChoice = this.context.hasChoice;
  protected readonly active = this.context.active;
  protected readonly activeLabel = this.context.activeLabel;
  protected readonly available = this.context.available;

  protected select(role: Role): void {
    this.context.select(role);
  }
}
