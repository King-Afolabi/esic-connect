import { test, expect } from '@playwright/test';
import { ACCOUNTS, DEMO_DATA } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * DOMAINE 2 (partiel) — Référentiels pédagogiques, organisation, alternance.
 *
 * `/academic` est en LECTURE SEULE dans ce prototype (CURRENT-STATE.md) :
 * pas de test de création/édition ici, seulement de consultation — écrire
 * un test qui cliquerait un bouton « Créer » inexistant serait un test
 * inventé.
 *
 * Chaque page distincte est atteinte par un `loginAsUi(page, account, path)`
 * dédié (voir `tests/support/auth.ts`) : une session ne survit qu'à des
 * navigations internes (clics), jamais à un second `page.goto`.
 */

test.describe('Référentiel académique (lecture seule)', () => {
  test('ADMIN : les années scolaires réelles s\'affichent', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/academic');
    await expect(page).toHaveURL(/\/academic\/academic-years$/);
    await expect(page.getByText(DEMO_DATA.academicYearCode)).toBeVisible();
  });

  test('ADMIN : les formations réelles s\'affichent', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/academic/programs');
    await expect(page.getByText(DEMO_DATA.programCode)).toBeVisible();
  });

  test('ADMIN : les classes réelles s\'affichent', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/academic/class-groups');
    await expect(page.getByText(DEMO_DATA.classCode)).toBeVisible();
  });

  test('un PEDAGOGICAL_MANAGER peut consulter (périmètre serveur, pas de 403 sur son propre périmètre)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/academic/class-groups');
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
  });
});

test.describe('Organisation — sites (lecture pour tous les rôles habilités, écriture ADMIN/SUPER_ADMIN)', () => {
  test('le site de démonstration réel apparaît dans la liste', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.SUPER_ADMIN, '/organization/sites');
    await expect(page.getByText(DEMO_DATA.siteCode)).toBeVisible();
  });

  test('ADMIN voit le bouton "Nouveau site"', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites');
    await expect(page.getByRole('link', { name: 'Nouveau site' })).toBeVisible();
  });

  test('PEDAGOGICAL_MANAGER ne voit pas "Nouveau site" (lecture seule)', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/organization/sites');
    await expect(page.getByRole('link', { name: 'Nouveau site' })).toHaveCount(0);
  });

  test('création de site : validation des champs obligatoires, puis création réelle', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites/new');

    // Soumission à vide → erreurs de validation, pas de requête serveur.
    await page.getByRole('button', { name: 'Créer le site' }).click();
    await expect(page.getByText('Le code est obligatoire.')).toBeVisible();
    await expect(page.getByText('Le nom est obligatoire')).toBeVisible();

    const uniqueCode = `E2E-${Date.now()}`;
    await page.getByLabel('Code', { exact: true }).fill(uniqueCode);
    await page.getByLabel('Nom').fill('Site créé par l\'audit E2E');
    await page.getByLabel('Fuseau horaire (IANA)').fill('Europe/Paris');
    await page.getByRole('button', { name: 'Créer le site' }).click();

    await expect(page).toHaveURL(/\/organization\/sites\/[0-9a-f-]+$/, { timeout: 10_000 });
    // Le code apparaît deux fois (titre "Site {code}" + fiche détaillée) :
    // `exact: true` cible la valeur exacte de la fiche, pas le titre.
    await expect(page.getByText(uniqueCode, { exact: true })).toBeVisible();
  });

  test('code de site dupliqué : le serveur refuse et le formulaire affiche l\'erreur (pas de doublon silencieux)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites/new');
    await page.getByLabel('Code', { exact: true }).fill(DEMO_DATA.siteCode); // code déjà utilisé par le site de démo
    await page.getByLabel('Nom').fill('Tentative de doublon');
    await page.getByLabel('Fuseau horaire (IANA)').fill('Europe/Paris');
    await page.getByRole('button', { name: 'Créer le site' }).click();
    // Le formulaire reste affiché (pas de navigation) et une erreur serveur
    // est reportée sur le champ concerné.
    await expect(page).toHaveURL(/\/organization\/sites\/new$/);
    await expect(page.locator('mat-error')).not.toHaveCount(0);
  });
});

test.describe('Alternance', () => {
  test('TEACHER seul (sans PEDAGOGICAL_MANAGER) : route inaccessible', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, '/alternation');
    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('ADMIN : accès en lecture + écriture aux modèles de rythme', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/alternation/patterns');
    await expect(page.getByRole('heading', { name: "Modèles de rythme d'alternance" })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Nouveau modèle' })).toBeVisible();
  });

  test('PEDAGOGICAL_MANAGER : lecture seule des modèles (pas de bouton de création)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/alternation/patterns');
    await expect(page.getByRole('heading', { name: "Modèles de rythme d'alternance" })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Nouveau modèle' })).toHaveCount(0);
  });

  test('PEDAGOGICAL_MANAGER : écriture protégée par URL directe (pas seulement le bouton masqué)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/alternation/patterns/new');
    await expect(page).toHaveURL(/\/forbidden$/);
  });
});
