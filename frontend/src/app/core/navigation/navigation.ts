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
    // Écran livré : import CSV contrôlé des apprenants (simulation puis
    // confirmation). Périmètre aligné sur `StudentImportWeb.MANAGE_ROLES` ;
    // un `PEDAGOGICAL_MANAGER` reste limité à son périmètre côté serveur.
    label: 'Import apprenants',
    path: '/students/import',
    icon: 'upload_file',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
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
    // Écran livré : référentiel organisationnel (sites → fiche →
    // création / modification, puis bâtiments, salles et plages réseau
    // depuis la fiche d'un site). Périmètre aligné sur
    // `SiteController.READ_ROLES` ; l'écriture des sites est restreinte
    // plus finement par la route, et les plages réseau restent
    // `SUPER_ADMIN` côté serveur.
    label: 'Organisation',
    path: '/organization',
    icon: 'apartment',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    // Écran livré : import CSV, simulation, revue et publication d'un
    // planning de classe, puis consultation des versions publiées
    // (`com.esic.connect.planning`). Périmètre aligné sur
    // `PlanningWeb.MANAGE_ROLES` ; un `PEDAGOGICAL_MANAGER` reste limité à
    // son périmètre côté serveur.
    label: 'Planning',
    path: '/planning',
    icon: 'calendar_month',
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
  {
    // Écran livré : séances exceptionnelles et émargement (liste,
    // création, ouverture / fermeture, QR + code court, présences).
    // Périmètre aligné sur `CourseSessionWeb.READ_ROLES` ; un `TEACHER`
    // ne voit que ses séances (décidé côté serveur).
    label: 'Séances',
    path: '/sessions',
    icon: 'event_available',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER', 'TEACHER'],
  },
  {
    // Écran livré : émargement de l'apprenant par code court
    // (`AttendanceWeb.VALIDATE_ROLE` = `STUDENT` uniquement).
    label: 'Émargement',
    path: '/attendance',
    icon: 'how_to_reg',
    roles: ['STUDENT'],
  },
  {
    // Écran livré : espace « Mes présences » de l'apprenant (V10) —
    // historique, dépôt et suivi d'un justificatif métier.
    label: 'Mes présences',
    path: '/my-attendance',
    icon: 'fact_check',
    roles: ['STUDENT'],
  },
  {
    // Écran livré : suivi d'assiduité (V10) — synthèse, rapports par
    // séance / classe / apprenant, file des justificatifs. Périmètre
    // aligné sur `AttendanceManagementWeb.REPORT_ROLES` ; un
    // `PEDAGOGICAL_MANAGER` reste filtré par périmètre côté serveur.
    label: "Suivi d'assiduité",
    path: '/attendance-management',
    icon: 'insights',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    // Écran livré (G1-D) : centre de notifications de l'appelant —
    // liste paginée, filtre lu / non lu, marquage lu / tout lu.
    // `@PreAuthorize("isAuthenticated()")` : visible par tout rôle.
    label: 'Notifications',
    path: '/notifications',
    icon: 'notifications',
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
