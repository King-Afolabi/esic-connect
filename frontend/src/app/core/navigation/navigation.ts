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
  /**
   * Chemins supplémentaires « possédés » par cette entrée : sur une URL
   * qui en préfixe un, l'entrée est considérée active bien que son `path`
   * diffère. Sert au regroupement « Organisation & planning », dont le
   * hub porte `/organisation-planning` mais qui doit rester actif sur
   * `/academic`, `/organization`, `/planning`, `/alternation` et leurs
   * sous-routes. N'élargit **jamais** un droit : `visibleNavItems`
   * n'utilise que `roles`.
   */
  matchPaths?: readonly string[];
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
    // d'inscriptions. Consultation alignée sur `EnrollmentWeb.READ_ROLES` :
    // l'administration (ADMIN / SUPER_ADMIN / SCHOOL_ADMINISTRATION) a
    // l'accès global ; le PEDAGOGICAL_MANAGER et le TEACHER n'y voient
    // que les apprenants de leur périmètre (classes de leurs formations /
    // de leurs séances), restreint côté serveur par `RosterScopeResolver`
    // — jamais de fuite inter-formations. La création manuelle et l'import
    // restent réservés aux rôles d'administration (boutons masqués pour
    // les deux autres).
    //
    // Regroupement (ANO-NAV-001) : l'import CSV (`/students/import`) et la
    // création manuelle (`/students/nouveau`) ne sont pas des entrées
    // racines distinctes — ce sont des sous-écrans de « Apprenants », avec
    // un `.esic-back` vers `/students` et un accès depuis l'en-tête de la
    // liste. `matchPaths` garde donc « Apprenants » actif sur ces routes.
    // Les routes elles-mêmes sont inchangées (liens profonds préservés).
    label: 'Apprenants',
    path: '/students',
    icon: 'groups',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER', 'TEACHER'],
    matchPaths: ['/students/import', '/students/nouveau'],
  },
  {
    // Regroupement (Lot §4) : une seule entrée latérale pour les quatre
    // sous-sections auparavant séparées — référentiels académiques,
    // organisation physique, planning, alternance. Le hub
    // `/organisation-planning` ne fait que lancer : les routes `/academic`,
    // `/organization`, `/planning`, `/alternation` sont inchangées (favoris,
    // liens directs). `matchPaths` garde cette entrée active sur toutes ces
    // sous-routes. Périmètre : union des rôles de lecture des quatre (ils
    // partagent le même), le serveur restant l'autorité.
    label: 'Organisation & planning',
    path: '/organisation-planning',
    icon: 'apartment',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
    matchPaths: ['/academic', '/organization', '/planning', '/alternation'],
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
    // Écran livré : journal de transparence (EF-ATT-014). Le serveur le
    // bâtit depuis le seul JWT ; aucun autre rôle n'y a accès.
    label: 'Journal de transparence',
    path: '/my-attendance/transparency',
    icon: 'history',
    roles: ['STUDENT'],
  },
  {
    // Écran livré : départ anticipé (EF-ATT-013). Signaler n'est pas
    // être autorisé — l'écran le dit.
    label: 'Départs anticipés',
    path: '/my-attendance/early-departures',
    icon: 'logout',
    roles: ['STUDENT'],
  },
  {
    // Écran livré : réclamations (EF-CLAIM-001..004). L'API n'exige que
    // d'être authentifié, mais un compte SANS rôle actif n'a ni
    // interlocuteur ni périmètre : lui proposer l'écran l'enverrait vers
    // une liste vide. Les six rôles sont donc listés explicitement.
    label: 'Réclamations',
    path: '/claims',
    icon: 'forum',
    roles: [
      'STUDENT',
      'TEACHER',
      'PEDAGOGICAL_MANAGER',
      'SCHOOL_ADMINISTRATION',
      'ADMIN',
      'SUPER_ADMIN',
    ],
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
    // Écran livré (G1-D, sprint 10) : espace « Notifications » — une
    // seule entrée latérale ouvrant deux vues internes en onglets (liste
    // paginée avec filtre lu / non lu et marquage ; préférences par
    // catégorie et par canal, EF-NOTIF-006). `activeNavPath` garde cette
    // entrée — et elle seule — active sur `/notifications` comme sur
    // `/notifications/preferences`. `@PreAuthorize("isAuthenticated()")` :
    // visible par tout rôle.
    label: 'Notifications',
    path: '/notifications',
    icon: 'notifications',
  },
  {
    // Écran livré (sprint 11) : recherche globale dans le périmètre de
    // l'appelant (EF-USER-009). Périmètre aligné sur
    // `GlobalSearchController` — un formateur et un apprenant en sont
    // exclus : leur besoin est couvert par leurs propres écrans.
    label: 'Recherche globale',
    path: '/recherche',
    icon: 'search',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    // Écran livré (sprint 11) : attestations d'assiduité (EF-REP-006).
    label: 'Attestations',
    path: '/attestations',
    icon: 'workspace_premium',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
  },
  {
    // Écran livré (CDC §5.6/§5.7, `com.esic.connect.myplanning`) : consultation
    // du planning en application — distincte de l'abonnement calendrier
    // ci-dessous (agenda externe). Un formateur voyait déjà ses séances
    // via `GET /api/v1/sessions`, mais sans écran dédié ; un apprenant n'en
    // avait aucune (constaté en recette, `STUDENT` est exclu de ces routes)
    // — ce manque motive ce nouvel écran.
    label: 'Mon planning',
    path: '/mon-planning',
    icon: 'event_note',
    roles: ['TEACHER', 'STUDENT'],
  },
  {
    // Écran livré (sprint 11) : abonnement iCalendar au planning
    // (EF-INT-001). Visible par tout rôle : chacun s'abonne au sien, et
    // le serveur dérive le périmètre du sujet du jeton.
    label: 'Abonnement calendrier',
    path: '/mon-compte/calendrier',
    icon: 'event_available',
  },
  {
    // Écran livré (sprint 11) : consultation et export de la piste
    // d'audit (EF-AUD-002). Réservé à l'administration.
    label: "Piste d'audit",
    path: '/exploitation/audit',
    icon: 'fact_check',
    roles: ['ADMIN', 'SUPER_ADMIN'],
  },
  {
    // Écran livré (sprint 10) : file d'échec des effets de bord et rejeu
    // manuel (EF-OPS-005). Périmètre aligné sur `OutboxAdminController`
    // (`ADMIN` / `SUPER_ADMIN`) : un rejeu peut envoyer un courriel.
    label: 'Effets de bord',
    path: '/exploitation/effets-de-bord',
    icon: 'sync_problem',
    roles: ['ADMIN', 'SUPER_ADMIN'],
  },
  {
    // Écran livré (sprint 3) : référentiel des matières (EF-ACA-006).
    // Lecture ouverte aux formateurs — ils qualifient leurs séances.
    label: 'Matières',
    path: '/subjects',
    icon: 'menu_book',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER', 'TEACHER'],
  },
  {
    // Écran livré (sprint 3) : suivi des invitations et de la
    // délivrabilité des courriels (EF-USER-007, EF-USER-008).
    //
    // Regroupement (ANO-NAV-001) : le rapport des invitations non activées
    // (`/invitations/non-activees`, EF-REP-010) n'est plus une entrée
    // racine distincte — c'est une vue de la page « Invitations », reliée
    // par une sous-navigation `.esic-subnav` présente sur les deux écrans.
    // `matchPaths` garde « Invitations » actif sur cette route ; la route
    // est inchangée (lien profond préservé).
    label: 'Invitations',
    path: '/invitations',
    icon: 'mark_email_read',
    roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'],
    matchPaths: ['/invitations/non-activees'],
  },
  {
    // Écran livré (sprint 2) : sécurité du compte de l'appelant — second
    // facteur, clés d'accès, appareils reconnus. Visible par tout rôle :
    // chacun gère ses propres moyens d'authentification, et le serveur
    // déduit le périmètre du sujet du jeton.
    label: 'Sécurité de mon compte',
    path: '/mon-compte/securite',
    icon: 'security',
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

/** Segmente un chemin ou une URL, sans la chaîne de requête ni le fragment. */
function toSegments(pathOrUrl: string): string[] {
  const clean = pathOrUrl.split(/[?#]/)[0];
  return clean.split('/').filter((segment) => segment.length > 0);
}

/**
 * Résout l'**unique** entrée de navigation active pour une URL donnée : le
 * `path` du menu le plus profond dont tous les segments préfixent ceux de
 * l'URL courante.
 *
 * Corrige le double marquage des routes imbriquées (Lot C) : sur
 * `/notifications/preferences`, seule « Préférences de notification » est
 * active, pas « Notifications » ; sur `/students/42` (fiche hors menu),
 * c'est « Apprenants » qui reste le parent actif ; sur `/students/import`,
 * c'est « Import apprenants ». Une correspondance par simple préfixe de
 * chaîne (`startsWith`) marquerait plusieurs entrées et confondrait
 * `/attendance` avec `/attendance-management` — d'où la comparaison
 * **segment par segment**.
 *
 * @returns le `path` actif, ou `null` si l'URL ne relève d'aucune entrée.
 */
export function activeNavPath(url: string, items: readonly NavItem[]): string | null {
  const current = toSegments(url);
  let best: string | null = null;
  let bestDepth = 0;
  for (const item of items) {
    // Le `path` de l'entrée, plus les chemins qu'elle « possède »
    // (`matchPaths`) : le regroupement « Organisation & planning » reste
    // actif sur `/academic`, `/planning`, etc. La correspondance la plus
    // profonde l'emporte toujours — une sous-route qui a sa propre entrée
    // (rare ici) resterait prioritaire.
    for (const candidate of [item.path, ...(item.matchPaths ?? [])]) {
      const segments = toSegments(candidate);
      if (segments.length === 0 || segments.length > current.length) {
        continue;
      }
      const matches = segments.every((segment, index) => segment === current[index]);
      if (matches && segments.length > bestDepth) {
        best = item.path;
        bestDepth = segments.length;
      }
    }
  }
  return best;
}
