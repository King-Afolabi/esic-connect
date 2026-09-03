import { test, expect, devices } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * DOMAINE 9 — Performance et ergonomie.
 *
 * Portée volontairement limitée : `docs/01-cadrage.md` §17.5 fixe
 * l'objectif « < 100 ms » pour des **routes API simples servies depuis le
 * cache Redis**, mesuré côté serveur — PAS pour un temps de chargement de
 * page complète mesuré depuis un navigateur (réseau, rendu Angular,
 * Material inclus). Les mesures ci-dessous sont donc **indicatives**
 * (temps de chargement perçu) et ne valident PAS l'objectif du cahier des
 * charges ; les prétendre équivalentes serait inventer un résultat.
 */

test.describe('Temps de chargement — indicatif, pas une validation de l\'objectif < 100 ms', () => {
  for (const [label, path] of [
    ['tableau de bord', '/dashboard'],
    ['liste des apprenants', '/students'],
    ['liste des séances', '/sessions'],
  ] as const) {
    test(`chargement de ${label} : mesure indicative rapportée (aucun seuil dur imposé)`, async ({
      page,
    }) => {
      // Mesure la connexion + l'arrivée sur la page cible (via le
      // mécanisme `redirect=`, la seule façon d'atteindre cette page sans
      // perdre la session — voir support/auth.ts) : inclut donc le coût de
      // connexion, ce qui est explicitement documenté ci-dessous plutôt que
      // présenté comme un chargement de page isolé.
      const start = Date.now();
      await loginAsUi(page, ACCOUNTS.ADMIN, path);
      const elapsedMs = Date.now() - start;
      test.info().annotations.push({
        type: 'perf-indicative-ms',
        description: `connexion + ${path} → ${elapsedMs} ms (environnement local, 1 utilisateur, sans mise en charge)`,
      });
      await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
      // Seuil large : détecte une régression grossière (page qui ne charge
      // jamais), pas une mesure de performance fine.
      expect(elapsedMs).toBeLessThan(15_000);
    });
  }
});

test.describe('Responsive', () => {
  test('mobile (Pixel 7) : la navigation passe en tiroir avec bouton hamburger', async ({
    browser,
  }) => {
    const context = await browser.newContext({ ...devices['Pixel 7'] });
    const page = await context.newPage();
    await loginAsUi(page, ACCOUNTS.ADMIN);
    await expect(
      page.getByRole('button', { name: 'Ouvrir ou fermer la navigation' }),
    ).toBeVisible();
    await context.close();
  });

  test('desktop large : la navigation latérale est visible sans bouton hamburger', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await loginAsUi(page, ACCOUNTS.ADMIN);
    await expect(
      page.getByRole('button', { name: 'Ouvrir ou fermer la navigation' }),
    ).toHaveCount(0);
    await expect(page.locator('mat-sidenav')).toBeVisible();
  });
});

test.describe('Accessibilité — vérifications structurelles légères (pas un audit WCAG outillé complet)', () => {
  test('lien d\'évitement présent sur l\'interface authentifiée (app-shell)', async ({ page }) => {
    // Le lien d'évitement est désormais un composant partagé
    // (`core/a11y/skip-link`) utilisé par `AppShell` ET par les pages
    // publiques — correction du finding F-A11Y-1 (3 septembre 2026).
    await loginAsUi(page, ACCOUNTS.ADMIN);
    await expect(page.getByRole('link', { name: 'Aller au contenu principal' })).toBeAttached();
    await expect(page.locator('#main-content')).toBeAttached();
  });

  test('repère principal ET lien d\'évitement présents sur la page de connexion (F-A11Y-1 corrigé)', async ({
    page,
  }) => {
    await page.goto('/login');
    await expect(page.locator('#main-content')).toBeAttached();
    await expect(page.getByRole('link', { name: 'Aller au contenu principal' })).toBeAttached();
  });

  test('navigation clavier : Tab puis Entrée soumet le formulaire de connexion', async ({
    page,
  }) => {
    await page.goto('/login');
    await page.getByLabel('Adresse électronique').fill(ACCOUNTS.ADMIN.email);
    await page.getByLabel('Mot de passe').fill(ACCOUNTS.ADMIN.password);
    await page.getByLabel('Mot de passe').press('Tab');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 10_000 });
  });

  test('chaque icône décorative de l\'en-tête est masquée aux technologies d\'assistance', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN);
    const icons = page.locator('.shell__toolbar mat-icon');
    const count = await icons.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i += 1) {
      await expect(icons.nth(i)).toHaveAttribute('aria-hidden', 'true');
    }
  });

  test('erreur de connexion annoncée via role="alert" (lecteur d\'écran)', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Adresse électronique').fill(ACCOUNTS.STUDENT.email);
    await page.getByLabel('Mot de passe').fill('faux');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    // Le conteneur `role="alert"` existe en permanence (`login.html`) :
    // seul un contenu non vide prouve qu'une erreur a bien été annoncée.
    await expect(page.getByRole('alert')).not.toBeEmpty({ timeout: 10_000 });
  });
});
