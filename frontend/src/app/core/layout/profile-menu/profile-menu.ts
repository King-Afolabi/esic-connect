import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../auth/auth.service';
import { RoleContextService } from '../../auth/role-context.service';
import { roleLabel } from '../../models/role';

/**
 * Panneau compact « Profil » ancré sous un déclencheur dans l'en-tête
 * (Lot §3).
 *
 * <p>But : sortir l'adresse et les rôles de la barre supérieure, qui les
 * affichait en clair et à plat, et les regrouper dans un petit panneau
 * ouvert à la demande. Le déclencheur conserve un identifiant court pour
 * que l'en-tête ne devienne pas ambigu.
 *
 * <p>Le panneau n'affiche que des informations <strong>déjà chargées</strong>
 * (session en mémoire, contexte de rôle) : aucun appel réseau. Aucune
 * donnée sensible — ni jeton, ni identifiant interne, ni secret. Pas de
 * bouton « copier » : le texte reste simplement sélectionnable.
 *
 * <p>{@link MatMenuModule} fournit l'ouverture au clic et au clavier, la
 * fermeture par Échap et par clic extérieur, le piège de focus, le
 * repositionnement près des bords, et pose `aria-haspopup`,
 * `aria-expanded` et `aria-controls` sur le déclencheur. La déconnexion
 * reste un contrôle distinct de l'en-tête : elle n'est pas dupliquée ici.
 */
@Component({
  selector: 'app-profile-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatMenuModule, RouterLink],
  templateUrl: './profile-menu.html',
  styleUrl: './profile-menu.scss',
})
export class ProfileMenu {
  private readonly auth = inject(AuthService);
  private readonly context = inject(RoleContextService);

  protected readonly roleLabel = roleLabel;

  protected readonly email = this.auth.currentUserEmail;
  protected readonly roles = this.auth.roles;

  /** Libellé du contexte d'usage actif, uniquement si le compte cumule des rôles. */
  protected readonly contextLabel = computed(() =>
    this.context.hasChoice() ? this.context.activeLabel() : null,
  );

  /**
   * Identifiant court gardé visible dans l'en-tête : la partie locale de
   * l'adresse (avant `@`), sinon « Profil ». Jamais l'adresse complète —
   * c'est précisément ce qu'on déplace dans le panneau.
   */
  protected readonly shortName = computed(() => {
    const address = this.email();
    if (!address) {
      return 'Profil';
    }
    const at = address.indexOf('@');
    return at > 0 ? address.slice(0, at) : address;
  });

  /** Page de gestion du compte existante (sécurité, appareils, passkeys). */
  protected readonly securityLink = '/mon-compte/securite';
}
