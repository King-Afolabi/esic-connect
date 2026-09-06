import { test, expect, Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';

import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * Captures pour le rapport (Lot §9) — écrans AUTHENTIFIÉS de la passe
 * « navigation & dashboard réorganisés » : Notifications unifiées, vues
 * du suivi d'assiduité, tableau de bord compacté, hub Organisation &
 * planning, plus les captures métier précédemment manquantes.
 *
 * Pile locale de démonstration (profil Spring `demo`, base
 * `esic_connect_demo`) — ce n'est PAS un déploiement. Second facteur
 * franchi réellement (`ESIC_DEMO_TOTP_SECRET`).
 *
 * Nommage : `NN-zone-etat-viewport.png` sous `artifacts/report-screenshots/`.
 */

const SHOTS = 'artifacts/report-screenshots';
mkdirSync(SHOTS, { recursive: true });

const DESKTOP = { width: 1440, height: 900 };
const MOBILE = { width: 390, height: 844 };

async function shot(page: Page, name: string): Promise<void> {
  await page.waitForTimeout(250); // laisse la transition de route se poser
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true });
}

test.describe('Notifications — espace unifié (Lot §1)', () => {
  test('vue Notifications / vue Préférences / retour sans état résiduel', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/notifications');
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
    await shot(page, '01-notifications-liste-active-desktop-1440x900');

    await page.getByRole('link', { name: 'Préférences' }).click();
    await expect(page).toHaveURL(/\/notifications\/preferences$/);
    await shot(page, '02-notifications-preferences-active-desktop-1440x900');

    // Retour vers la vue liste par l'onglet interne : un seul onglet
    // actif, une seule entrée latérale active — aucun état résiduel.
    await page.locator('.esic-subnav__link', { hasText: 'Notifications' }).click();
    await expect(page).toHaveURL(/\/notifications$/);
    const activeTabs = page.locator('.esic-subnav__link[aria-current="page"]');
    await expect(activeTabs).toHaveCount(1);
    await expect(activeTabs).toHaveText('Notifications');
    await expect(page.locator('.shell__nav a[aria-current="page"]')).toHaveCount(1);
    await shot(page, '03-notifications-retour-sans-etat-residuel-desktop-1440x900');

    await page.setViewportSize(MOBILE);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
    await shot(page, '04-notifications-liste-active-mobile-390x844');
    await page.getByRole('link', { name: 'Préférences' }).click();
    await shot(page, '05-notifications-preferences-active-mobile-390x844');
  });
});

test.describe("Suivi d'assiduité — vues clarifiées (Lot §2)", () => {
  const views: Array<[label: string, slug: string, file: string]> = [
    ['Synthèse', 'summary', 'synthese'],
    ['Par séance', 'sessions', 'par-seance'],
    ['Par classe', 'classes', 'par-classe'],
    ['Par apprenant', 'students', 'par-apprenant'],
  ];

  test('les cinq vues et leur navigation secondaire', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    await loginAsUi(page, ACCOUNTS.ADMIN, '/attendance-management/summary');
    await expect(page.getByRole('heading', { name: "Suivi d'assiduité" })).toBeVisible();

    let i = 10;
    for (const [label, slug, file] of views) {
      await page.getByRole('link', { name: label }).click();
      await expect(page).toHaveURL(new RegExp(`/attendance-management/${slug}$`));
      const active = page.locator('.esic-subnav__link[aria-current="page"]');
      await expect(active).toHaveCount(1);
      await expect(active).toHaveText(label);
      await shot(page, `${i}-suivi-assiduite-${file}-desktop-1440x900`);
      i += 1;
    }

    // « Justificatifs » + un filtre de statut engagé (le plus proche d'un
    // sélecteur segmenté dans cet espace — il n'y a pas de switch booléen).
    await page.getByRole('link', { name: 'Justificatifs' }).click();
    await page.getByRole('button', { name: 'Acceptés' }).click();
    await shot(page, '14-suivi-assiduite-justificatifs-filtre-actif-desktop-1440x900');

    await page.setViewportSize(MOBILE);
    await page.goto('/attendance-management/students');
    await expect(page.getByRole('heading', { name: "Suivi d'assiduité" })).toBeVisible();
    await shot(page, '15-suivi-assiduite-par-apprenant-mobile-390x844');
  });
});

test.describe('Tableau de bord — compacté (Lot §3)', () => {
  const viewports: Array<[w: number, h: number, tag: string]> = [
    [1440, 900, '1440x900'],
    [1280, 720, '1280x720'],
    [390, 844, '390x844'],
    [844, 390, '844x390'],
    [768, 1024, '768x1024'],
    [1024, 768, '1024x768'],
  ];

  test('six points de rupture', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/dashboard');
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();

    let i = 20;
    for (const [w, h, tag] of viewports) {
      await page.setViewportSize({ width: w, height: h });
      await page.waitForTimeout(300);
      await shot(page, `${i}-dashboard-${tag}`);
      i += 1;
    }
  });
});

test.describe('Organisation & planning — regroupement (Lot §4)', () => {
  test('entrée principale active + hub + une sous-section', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    // ADMIN (rôle unique) : le contexte de rôle ne peut pas rabattre la
    // navigation sur un rôle sans l'entrée groupée.
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organisation-planning');
    await expect(page.getByRole('heading', { name: /Organisation/ })).toBeVisible();
    // L'entrée latérale groupée est active.
    await expect(
      page.locator('.shell__nav a[aria-current="page"]'),
    ).toContainText('Organisation & planning');
    await shot(page, '30-organisation-planning-hub-actif-desktop-1440x900');

    // Sous-section : la route d'origine est intacte, l'entrée groupée
    // reste active dessus.
    await page.getByRole('link', { name: /Référentiels académiques/ }).click();
    await expect(page).toHaveURL(/\/academic\/academic-years$/);
    await expect(
      page.locator('.shell__nav a[aria-current="page"]'),
    ).toContainText('Organisation & planning');
    await shot(page, '31-organisation-planning-sous-section-academic-desktop-1440x900');

    await page.goto('/planning/import');
    await expect(
      page.locator('.shell__nav a[aria-current="page"]'),
    ).toContainText('Organisation & planning');
    await shot(page, '32-organisation-planning-sous-section-planning-desktop-1440x900');

    await page.setViewportSize(MOBILE);
    await page.goto('/organisation-planning');
    await expect(page.getByRole('heading', { name: /Organisation/ })).toBeVisible();
    await shot(page, '33-organisation-planning-hub-mobile-390x844');
  });
});

test.describe('Captures métier complémentaires', () => {
  test('liste filtrée + retour avec filtres conservés', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    await loginAsUi(page, ACCOUNTS.ADMIN, '/students');
    await expect(page.getByRole('heading', { name: /Apprenants/ })).toBeVisible();
    const search = page.locator('.esic-filters input, input[type="search"], input[type="text"]').first();
    await search.waitFor({ state: 'visible', timeout: 10_000 });
    await search.fill('a');
    await page.waitForTimeout(700);
    await shot(page, '40-liste-apprenants-filtree-desktop-1440x900');
    // Ouvre une fiche puis revient : les filtres doivent être restaurés
    // depuis l'URL (Lot G).
    const firstRow = page.locator('table a').first();
    if (await firstRow.count()) {
      await firstRow.click();
      await page.goBack();
      await expect(page).toHaveURL(/[?&]q=/);
      await shot(page, '41-liste-apprenants-retour-filtres-conserves-desktop-1440x900');
    }
  });

  test('création manuelle d\'un apprenant', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    await loginAsUi(page, ACCOUNTS.ADMIN, '/students/nouveau');
    await expect(page.getByRole('heading', { name: /apprenant/i })).toBeVisible();
    await shot(page, '42-creation-manuelle-apprenant-desktop-1440x900');
  });

  test('avertissement d\'expiration de session', async ({ page }) => {
    await page.setViewportSize(DESKTOP);
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/dashboard');
    // Déclenche l'avertissement via l'API de test exposée par le service
    // (Lot A) si disponible, sinon capture l'écran au repos et documente.
    const shown = await page.evaluate(() => {
      const w = window as unknown as { __esicShowSessionWarning?: () => void };
      if (typeof w.__esicShowSessionWarning === 'function') {
        w.__esicShowSessionWarning();
        return true;
      }
      return false;
    });
    if (shown) {
      await expect(page.getByRole('alertdialog')).toBeVisible();
      await shot(page, '48-avertissement-expiration-session-desktop-1440x900');
    } else {
      test.info().annotations.push({
        type: 'note',
        description:
          "Aucun déclencheur de test exposé pour l'avertissement de session — capture omise, comportement couvert par session-timeout-warning.spec.",
      });
    }
  });
});
