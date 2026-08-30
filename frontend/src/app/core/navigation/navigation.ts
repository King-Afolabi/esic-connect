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
    // Écran livré : administration des comptes utilisateurs et de leurs
    // rôles en LECTURE SEULE (liste → fiche → historique des rôles).
    // Périmètre aligné sur `UserAccountController` `READ_ROLES`.
    label: 'Administration',
    path: '/administration',
    icon: 'admin_panel_settings',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'],
  },
  {
    // Écran livré : liste des profils apprenants + fiche + historique
    // d'inscriptions. Périmètre aligné sur `EnrollmentWeb.MANAGE_ROLES`.
    label: 'Apprenants',
    path: '/students',
    icon: 'groups',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'],
  },
  {
    // Écran livré : consultation en lecture seule du référentiel
    // académique (années scolaires → formations → niveaux → promotions →
    // classes). Périmètre aligné sur `AcademicWeb.READ_ROLES`.
    label: 'Référentiels',
    path: '/academic',
    icon: 'school',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    // Écran livré : gestion et consultation de l'alternance (modèles de
    // rythme, affectations aux classes, exceptions individuelles,
    // résolution de contexte). Périmètre aligné sur
    // `AlternationWeb.PATTERN_READ_ROLES` / `SCOPED_ROLES` ; l'écriture
    // des modèles est restreinte plus finement par la route.
    label: 'Alternance',
    path: '/alternation',
    icon: 'sync_alt',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
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
