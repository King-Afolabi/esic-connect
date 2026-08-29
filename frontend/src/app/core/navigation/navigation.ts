import { Role } from '../models/role';

export interface NavItem {
  label: string;
  path: string;
  icon: string;
  /**
   * Rôles autorisés à voir l'entrée, alignés sur le `@PreAuthorize` de la
   * route API correspondante. `undefined` = visible par tout utilisateur
   * authentifié.
   */
  roles?: readonly Role[];
  /**
   * Route protégée dont l'écran métier n'est pas encore livré : elle
   * reste directement adressable (utile pour les tests d'autorisation) et
   * gardée par rôle, mais n'est **jamais** rendue dans la navigation
   * principale ni dans les accès rapides tant que ce drapeau est vrai
   * ({@link visibleNavItems} l'exclut).
   */
  placeholder?: boolean;
}

/**
 * Matrice de navigation dérivée de la matrice d'autorisation du back-end
 * (docs/02-cahier-des-charges.md §6 ; `UserAccountController`,
 * `EnrollmentWeb.MANAGE_ROLES`).
 *
 * Les entrées `placeholder` sont conservées ici pour tracer le lien
 * rôle → route protégée, mais elles ne sont pas affichées : seuls les
 * écrans réellement utilisables apparaissent dans la navigation.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  {
    label: 'Tableau de bord',
    path: '/dashboard',
    icon: 'dashboard',
  },
  {
    label: 'Administration',
    path: '/administration',
    icon: 'admin_panel_settings',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    placeholder: true,
  },
  {
    label: 'Apprenants',
    path: '/students',
    icon: 'groups',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'],
    placeholder: true,
  },
];

/**
 * Entrées effectivement affichables : écran métier livré
 * (`placeholder` faux/absent) **et** rôle compatible.
 */
export function visibleNavItems(
  items: readonly NavItem[],
  heldRoles: readonly Role[],
): NavItem[] {
  return items.filter(
    (item) =>
      !item.placeholder && (!item.roles || item.roles.some((r) => heldRoles.includes(r))),
  );
}
