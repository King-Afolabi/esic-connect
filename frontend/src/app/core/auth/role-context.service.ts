import { computed, effect, Injectable, inject, signal, untracked } from '@angular/core';

import { Role, ROLES } from '../models/role';
import { AuthService } from './auth.service';

/**
 * Contexte d'utilisation (docs/02-cahier-des-charges.md §6.1,
 * exigence EF-AUTH-003 « Choisir un contexte de rôle »).
 *
 * Un compte cumulant plusieurs rôles doit pouvoir choisir sous quel rôle
 * il travaille. Ce choix ne pilote QUE l'affichage et la navigation côté
 * client : il ne remplace jamais le contrôle d'accès de Spring Security,
 * qui revalide chaque appel d'API à partir du JWT (docs/07 §7, RG-002,
 * RG-087). Sélectionner un contexte n'accorde donc aucun droit et n'en
 * retire aucun côté serveur.
 *
 * Contextes proposés = uniquement les rôles réellement présents dans le
 * claim `roles` du JWT ({@link AuthService.roles}). Aucune valeur inventée.
 *
 * Stockage : un signal en mémoire, au même titre que le jeton d'accès
 * (docs/07 §6, RG-085). Rien dans `localStorage` ni `sessionStorage`. Un
 * rechargement de page perd le contexte comme il perd la session.
 */
@Injectable({ providedIn: 'root' })
export class RoleContextService {
  private readonly auth = inject(AuthService);

  private readonly _active = signal<Role | null>(null);

  /** Rôles proposés comme contextes = rôles réellement présents dans le JWT. */
  readonly available = this.auth.roles;

  /** Contexte actif, ou `null` tant que le compte ne porte aucun rôle. */
  readonly active = this._active.asReadonly();

  /** Libellé lisible du contexte actif, ou `null`. */
  readonly activeLabel = computed(() => {
    const role = this._active();
    return role ? ROLE_CONTEXT_LABELS[role] : null;
  });

  /** Vrai lorsqu'un choix a du sens : au moins deux rôles cumulés. */
  readonly hasChoice = computed(() => this.available().length > 1);

  /**
   * Rôles « effectifs » pour filtrer l'affichage et la navigation : le
   * seul contexte actif s'il est défini, sinon l'ensemble des rôles.
   *
   * À n'utiliser QUE pour masquer ou montrer des entrées d'interface,
   * jamais comme décision d'autorisation.
   */
  readonly effectiveRoles = computed<readonly Role[]>(() => {
    const role = this._active();
    return role ? [role] : this.available();
  });

  constructor() {
    // Réaligne le contexte actif sur la session courante. Sans
    // persistance, toute nouvelle session (connexion, jeu de rôles
    // différent) repart du contexte par défaut ; un contexte encore
    // valide au regard des rôles courants est conservé tel quel.
    effect(() => {
      const roles = this.available();
      const current = untracked(this._active);
      if (current && roles.includes(current)) {
        return;
      }
      this._active.set(defaultContext(roles));
    });
  }

  /**
   * Sélectionne un contexte. Une valeur absente du JWT est ignorée
   * silencieusement : on ne fabrique jamais un rôle que le compte n'a pas.
   */
  select(role: Role): void {
    if (this.available().includes(role)) {
      this._active.set(role);
    }
  }
}

/** Libellés des contextes (repris des libellés de rôle, docs/02 §6.1). */
export const ROLE_CONTEXT_LABELS: Record<Role, string> = {
  SUPER_ADMIN: 'Supervision technique',
  ADMIN: 'Administration fonctionnelle',
  SCHOOL_ADMINISTRATION: 'Administration scolaire',
  PEDAGOGICAL_MANAGER: 'Gestion pédagogique',
  TEACHER: 'Mes séances de formateur',
  STUDENT: 'Mon espace apprenant',
};

/**
 * Contexte par défaut : le rôle le plus privilégié présent, en suivant
 * l'ordre déclaré de {@link ROLES}. `null` si le compte ne porte aucun
 * rôle connu.
 */
export function defaultContext(roles: readonly Role[]): Role | null {
  return ROLES.find((role) => roles.includes(role)) ?? null;
}
