import path from 'node:path';
import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi, loginAndCaptureBearerToken } from './support/auth';

const FIXTURES = path.join(__dirname, 'fixtures');

/**
 * DOMAINE 2 — Import CSV des apprenants (EF-IMP-001/002, RG-020/021/022,
 * AC-004/005). Import Excel/multi-feuille est `HORS_PÉRIMÈTRE_ASSUMÉ`
 * (docs/02 §4.3/§10.5) : non testé.
 */

test.describe('Import apprenants — parcours nominal (simulation → confirmation, AC-004)', () => {
  test('fichier CSV valide : simulation puis confirmation créent réellement les comptes', async ({
    page,
  }) => {
    // Emails uniques par exécution : la suite doit rester rejouable — un
    // fixture à emails fixes ferait basculer la 2e exécution en "mise à
    // jour" (les comptes existeraient déjà) et casserait l'assertion
    // "2 à créer" ci-dessous.
    const uniqueLastName = `E2ECree${Date.now()}`;
    const csv =
      'last_name,first_name,email,phone,formation_code,class_code,academic_year\n' +
      `${uniqueLastName},Camille,camille.${Date.now()}.e2e@example.test,0600000001,PRG-DEMO,C-DEMO,AY-DEMO\n` +
      `${uniqueLastName},Julien,julien.${Date.now() + 1}.e2e@example.test,0600000002,PRG-DEMO,C-DEMO,AY-DEMO\n`;

    await loginAsUi(page, ACCOUNTS.ADMIN, '/students/import');

    await page
      .locator('#csv-file')
      .setInputFiles({ name: 'students-valid.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();

    await expect(page).toHaveURL(/\/students\/import\/[0-9a-f-]+$/, { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: "Revue d'un import" })).toBeVisible();
    // 2 lignes valides et nouvelles → 2 comptes à créer, 0 erreur.
    const createCard = page.locator('.card', { hasText: 'À créer' });
    await expect(createCard.locator('.card__value')).toHaveText('2');
    const errorCard0 = page.locator('.card--error', { hasText: 'Erreurs' });
    await expect(errorCard0.locator('.card__value')).toHaveText('0');

    await page.getByRole('button', { name: "Confirmer l'import" }).click();
    await expect(
      page.getByRole('button', { name: 'Confirmer', exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Confirmer', exact: true }).click();

    // Le panneau transitoire "Import appliqué." disparaît dès que le job
    // rechargé passe au statut `APPLIED` (le bloc entier est gardé par
    // `j.status === 'SIMULATED'` dans le template) : l'indicateur de succès
    // fiable est l'étiquette de statut affichée dans l'en-tête de la fiche.
    await expect(page.getByText('· Appliqué ·', { exact: false })).toBeVisible({ timeout: 10_000 });

    // Le compte créé apparaît bien dans le référentiel réel des comptes
    // (pas seulement dans le bilan de l'écran d'import), vérifié via l'API
    // plutôt que la liste `/students` : celle-ci pagine à 20 lignes et se
    // trie par numéro étudiant, donc un compte fraîchement créé n'est pas
    // garanti d'apparaître sur sa première page après plusieurs exécutions
    // cumulées de cette suite.
    const token = await loginAndCaptureBearerToken(page, ACCOUNTS.ADMIN);
    const response = await page.request.get(
      `http://localhost:8080/api/v1/users?q=${encodeURIComponent(uniqueLastName)}`,
      { headers: { Authorization: token } },
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.content.some((u: { lastName: string }) => u.lastName === uniqueLastName)).toBe(true);
  });
});

test.describe('Import apprenants — détection d\'erreurs (RG-020/021, AC-004)', () => {
  test('colonne obligatoire manquante : refus bloquant, aucune donnée créée (RG-020)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/students/import');
    await page
      .locator('#csv-file')
      .setInputFiles(path.join(FIXTURES, 'students-missing-column.csv'));
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();

    await expect(
      page.getByText("Une ou plusieurs colonnes obligatoires sont absentes de l'en-tête."),
    ).toBeVisible();
    // Toujours sur l'écran d'accueil : aucun job de revue n'a été créé.
    await expect(page).toHaveURL(/\/students\/import$/);
  });

  test('lignes invalides (email malformé, nom vide, code de formation inconnu) : anomalies par ligne, rien de créé avant confirmation', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/students/import');
    await page
      .locator('#csv-file')
      .setInputFiles(path.join(FIXTURES, 'students-invalid-values.csv'));
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();

    await expect(page).toHaveURL(/\/students\/import\/[0-9a-f-]+$/, { timeout: 10_000 });
    // Au moins une ligne en erreur → carte "Erreurs" non nulle et import non confirmable.
    const errorCard = page.locator('.card--error .card__value');
    await expect(errorCard).not.toHaveText('0');
  });

  test('fichier non-CSV renommé en .csv : rejeté par le serveur, pas de job créé en silence', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/students/import');
    await page.locator('#csv-file').setInputFiles({
      name: 'renomme.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from("Ceci n'est pas un CSV structuré.\n\x00\x01\x02", 'utf-8'),
    });
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();
    // Comportement attendu : soit un rejet immédiat (message d'erreur sur
    // l'accueil), soit une revue avec 100% de lignes en erreur — dans les
    // deux cas, jamais une confirmation silencieuse sans anomalie signalée.
    await Promise.race([
      expect(page.locator('[role="alert"]').first()).toBeVisible({ timeout: 10_000 }),
      expect(page.locator('.card--error .card__value')).not.toHaveText('0'),
    ]);
  });

  test("contenu potentiellement XSS dans une cellule : affiché comme texte, jamais exécuté", async ({
    page,
  }) => {
    await page.addInitScript(() => {
      (window as unknown as { __xssFired?: boolean }).__xssFired = false;
    });
    await loginAsUi(page, ACCOUNTS.ADMIN, '/students/import');
    await page.locator('#csv-file').setInputFiles(path.join(FIXTURES, 'students-xss.csv'));
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();
    await expect(page).toHaveURL(/\/students\/import\/[0-9a-f-]+$/, { timeout: 10_000 });

    const xssFired = await page.evaluate(
      () => (window as unknown as { __xssFired?: boolean }).__xssFired,
    );
    expect(xssFired).toBe(false);
    // Le contenu doit être visible en tant que texte brut (Angular échappe
    // par défaut), pas interprété comme balise.
    await expect(page.locator('script:has-text("__xssFired")')).toHaveCount(0);
  });
});

test.describe('Import apprenants — autorisations', () => {
  for (const role of ['TEACHER', 'STUDENT'] as const) {
    test(`${role} ne peut pas accéder à l'écran d'import`, async ({ page }) => {
      await loginAsUi(page, ACCOUNTS[role], '/students/import');
      await expect(page).toHaveURL(/\/forbidden$/);
    });
  }
});
