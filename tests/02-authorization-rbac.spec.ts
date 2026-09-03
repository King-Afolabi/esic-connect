import path from 'node:path';
import { test, expect, Page } from '@playwright/test';
import { ACCOUNTS, DemoAccount } from './support/accounts';
import { loginAsUi } from './support/auth';

const CAPTURES = path.join(__dirname, '..', 'captures');

/**
 * DOMAINE 1 (suite) — Autorisation par rôle (RBAC + périmètre).
 *
 * La matrice ci-dessous est recopiée **à l'identique** de
 * `frontend/src/app/core/navigation/navigation.ts` (`NAV_ITEMS`), qui est
 * elle-même alignée sur le `@PreAuthorize` de chaque contrôleur Spring
 * (voir les commentaires de `app.routes.ts`). Le garde Angular n'est qu'un
 * confort de navigation — l'autorité réelle est Spring Security côté
 * serveur (docs/07 §7) ; ces tests vérifient le comportement observable
 * réel (redirection `/forbidden`), pas une supposition sur le serveur.
 *
 * Chaque route est atteinte via `loginAsUi(page, account, route.path)`,
 * qui navigue vers `route.path` AVANT authentification (redirection
 * `/login?redirect=…` par `authGuard`/`roleGuard`) puis se connecte — la
 * seule façon d'atteindre une route protégée arbitraire sans perdre la
 * session en mémoire par une navigation dure supplémentaire (voir
 * `tests/support/auth.ts`).
 */
interface RouteExpectation {
  path: string;
  allowed: DemoAccount['role'][];
}

const ROUTES: RouteExpectation[] = [
  { path: '/administration', allowed: ['ADMIN', 'SUPER_ADMIN'] },
  { path: '/students', allowed: ['ADMIN', 'SUPER_ADMIN'] },
  {
    path: '/students/import',
    allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER'],
  },
  { path: '/academic', allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER'] },
  { path: '/organization', allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER'] },
  { path: '/planning', allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER'] },
  { path: '/alternation', allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER'] },
  {
    path: '/sessions',
    allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER', 'TEACHER'],
  },
  { path: '/attendance', allowed: ['STUDENT'] },
  { path: '/my-attendance', allowed: ['STUDENT'] },
  {
    path: '/attendance-management',
    allowed: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER_TEACHER'],
  },
  // Aucune restriction de rôle : visible par tout utilisateur authentifié.
  {
    path: '/notifications',
    allowed: ['ADMIN', 'SUPER_ADMIN', 'TEACHER', 'STUDENT', 'PEDAGOGICAL_MANAGER_TEACHER'],
  },
];

/** Landmark de la barre latérale (`app-shell.html`) — le tableau de bord
 *  affiche aussi des "Accès rapides" avec les mêmes libellés : toute
 *  assertion sur un lien de navigation doit être scopée ici pour éviter
 *  une correspondance ambiguë entre les deux zones. */
const sidebar = (page: Page) => page.getByRole('navigation', { name: 'Navigation principale' });

async function expectForbidden(page: Page) {
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.getByRole('heading', { name: 'Accès refusé' })).toBeVisible();
}

async function expectAllowed(page: Page, path: string) {
  // Certaines routes redirigent vers un enfant par défaut
  // (`/academic` → `/academic/academic-years`, `redirectTo` dans
  // `app.routes.ts`) : on vérifie que l'URL finale COMMENCE par `path`,
  // pas qu'elle s'y termine exactement.
  const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  await expect(page).toHaveURL(new RegExp(`${escaped}($|/)`));
  await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
}

test.describe('Matrice RBAC — chaque (rôle, route) atteinte indépendamment', () => {
  for (const account of Object.values(ACCOUNTS)) {
    for (const route of ROUTES) {
      const verdict = route.allowed.includes(account.role) ? 'autorisé' : 'refusé';
      test(`${account.role} sur ${route.path} → ${verdict}`, async ({ page }) => {
        await loginAsUi(page, account, route.path);
        if (route.allowed.includes(account.role)) {
          await expectAllowed(page, route.path);
        } else {
          await expectForbidden(page);
        }
      });
    }
  }
});

test.describe('Cas ciblés du cahier des charges', () => {
  test('un TEACHER ne peut pas créer de séance (SESSION_CREATE_ROLES l\'exclut)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, '/sessions/new');
    await expectForbidden(page);
  });

  test('un PEDAGOGICAL_MANAGER peut créer une séance', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/sessions/new');
    await expect(page.getByRole('heading', { name: 'Nouvelle séance exceptionnelle' })).toBeVisible();
  });

  test("un STUDENT tapant directement l'URL /administration est refusé (pas de simple masquage de menu)", async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/administration');
    await expectForbidden(page);
    await page.screenshot({
      path: path.join(CAPTURES, 'errors', 'acces-refuse-student-administration.png'),
      fullPage: true,
    });
  });

  test('un STUDENT tapant /organization/sites/new (écriture) est refusé', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/organization/sites/new');
    await expectForbidden(page);
  });

  test("un TEACHER (sans PEDAGOGICAL_MANAGER) ne voit pas l'organisation ni le planning dans la navigation", async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER);
    await expect(sidebar(page).getByRole('link', { name: 'Organisation' })).toHaveCount(0);
    await expect(sidebar(page).getByRole('link', { name: 'Planning' })).toHaveCount(0);
    // En revanche « Séances » doit être visible (SESSION_READ_ROLES l'inclut).
    await expect(sidebar(page).getByRole('link', { name: 'Séances' })).toBeVisible();
  });

  test('un ADMIN voit tous les écrans de gestion dans la navigation', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN);
    for (const label of [
      'Tableau de bord',
      'Administration',
      'Apprenants',
      'Import apprenants',
      'Référentiels',
      'Organisation',
      'Planning',
      'Alternance',
      'Séances',
      "Suivi d'assiduité",
      'Notifications',
    ]) {
      await expect(sidebar(page).getByRole('link', { name: label, exact: true })).toBeVisible();
    }
    // Écrans réservés à STUDENT : jamais montrés à un ADMIN.
    await expect(sidebar(page).getByRole('link', { name: 'Émargement' })).toHaveCount(0);
    await expect(sidebar(page).getByRole('link', { name: 'Mes présences' })).toHaveCount(0);
  });
});
