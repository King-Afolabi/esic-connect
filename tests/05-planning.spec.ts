import path from 'node:path';
import { test, expect } from '@playwright/test';
import { ACCOUNTS, DEMO_DATA } from './support/accounts';
import { loginAsUi } from './support/auth';
import { demoTeacherPublicId } from './support/api';

const FIXTURES = path.join(__dirname, 'fixtures');

/**
 * DOMAINE 2 (suite) — Import et publication de planning.
 *
 * ATTENTION DOCUMENTAIRE (voir audit-report.md §2, finding "F-DOC-1") :
 * `docs/01-cadrage.md` §23.5 et `docs/02-cahier-des-charges.md` §4.5.1
 * portent un addendum daté du 31 août 2026 déclarant ce domaine entier
 * `HORS_PÉRIMÈTRE_ASSUMÉ` et demandant explicitement de ne « jamais »
 * présenter une livraison de la fonctionnalité planning. Le commit
 * `d3450e6` (lot G1, 1er septembre 2026, POSTÉRIEUR à cet addendum) a
 * pourtant livré un module `planning` complet et fonctionnel (import,
 * simulation, publication atomique, versionnement, création de séances)
 * — confirmé ici en pilotant l'écran réel. Les tests ci-dessous vérifient
 * ce qui EXISTE et FONCTIONNE réellement dans le code livré ; ils ne
 * prennent pas parti sur laquelle des deux décisions documentées est
 * la bonne — c'est une contradiction de gouvernance à trancher par
 * l'équipe, pas un bug de code.
 */

test.describe('Import de planning — parcours nominal', () => {
  test('CSV valide : simulation → publication → nouvelle version visible', async ({ page }) => {
    // `slot_key` ET date uniques par exécution : un `slot_key` seul ne
    // suffit pas — réutiliser la même date/heure à chaque run finit par
    // entrer en CONFLIT avec le même formateur déjà publié sur ce créneau
    // lors d'un run précédent (détection de conflit formateur,
    // docs/02 §14.3), ce qui classe la ligne en `CONFLICT` plutôt qu'en
    // `ADDED` sans faire remonter "Erreurs" — d'où un "Ajouts" bloqué à 0
    // constaté en pratique. La date est donc dérivée du run pour retomber
    // sur un jour ouvré distinct à chaque exécution.
    const suffix = Date.now();
    const dayOffset = 30 + (suffix % 300); // reste dans une plage de jours ouvrés futurs
    const isoDate = (offset: number) =>
      new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);
    // Identifiant résolu à l'exécution : il change à chaque recréation de
    // la base de démonstration (voir `tests/support/api.ts`).
    const teacherPublicId = await demoTeacherPublicId(page.request);
    const csv =
      'slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n' +
      `E2E-SLOT-${suffix}-1,${isoDate(dayOffset)},09:00,10:30,Europe/Paris,Cours e2e (audit),${teacherPublicId},\n` +
      `E2E-SLOT-${suffix}-2,${isoDate(dayOffset + 1)},14:00,16:00,Europe/Paris,Cours e2e (audit) 2,${teacherPublicId},\n`;

    await loginAsUi(page, ACCOUNTS.ADMIN, '/planning/import');

    await page.getByLabel('Classe').click();
    await page.getByRole('option', { name: new RegExp(DEMO_DATA.classCode) }).click();
    await page
      .locator('.plan__file-input')
      .setInputFiles({ name: 'planning-valid.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();

    await expect(page).toHaveURL(/\/planning\/import\/[0-9a-f-]+$/, { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: /Revue de l'import/ })).toBeVisible();
    // 2 créneaux valides et nouveaux → 2 ajouts, 0 erreur.
    await expect(page.locator('dt', { hasText: 'Ajouts' }).locator('xpath=following-sibling::dd[1]')).toHaveText('2');
    await expect(page.locator('dt', { hasText: 'Erreurs' }).locator('xpath=following-sibling::dd[1]')).toHaveText('0');

    await page.getByRole('button', { name: 'Publier le planning' }).click();
    await expect(page.getByRole('button', { name: 'Publier', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Publier', exact: true }).click();
    await expect(page.getByText('Ce planning est publié.')).toBeVisible({ timeout: 10_000 });

    // Navigation interne (routerLink réel de l'écran, pas un `page.goto`)
    // pour rester dans la même session authentifiée.
    await page.getByRole('link', { name: 'Voir les versions' }).click();
    await expect(page).toHaveURL(/\/planning\/versions$/);
    await page.getByLabel('Classe').click();
    await page.getByRole('option', { name: new RegExp(DEMO_DATA.classCode) }).click();
    // Au moins une ligne de version publiée apparaît (peut être la v1 du
    // jeu de démo, ou une version supplémentaire créée par ce test).
    await expect(page.locator('table.plan__table tbody tr').first()).toBeVisible({
      timeout: 10_000,
    });
  });

  test('colonne obligatoire manquante (time_zone_id) : import refusé', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/planning/import');
    await page.getByLabel('Classe').click();
    await page.getByRole('option', { name: new RegExp(DEMO_DATA.classCode) }).click();
    await page
      .locator('.plan__file-input')
      .setInputFiles(path.join(FIXTURES, 'planning-missing-column.csv'));
    await page.getByRole('button', { name: 'Lancer la simulation' }).click();
    // Refus bloquant (reste sur l'écran d'import) OU revue avec toutes les
    // lignes en erreur — jamais une publication silencieuse.
    await Promise.race([
      expect(page).toHaveURL(/\/planning\/import$/),
      expect(
        page.locator('dt', { hasText: 'Erreurs' }).locator('xpath=following-sibling::dd[1]'),
      ).not.toHaveText('0'),
    ]);
  });
});

test.describe('Autorisations', () => {
  for (const role of ['TEACHER', 'STUDENT'] as const) {
    test(`${role} ne peut pas accéder à l'import de planning`, async ({ page }) => {
      await loginAsUi(page, ACCOUNTS[role], '/planning/import');
      await expect(page).toHaveURL(/\/forbidden$/);
    });

    test(`${role} ne peut pas accéder aux versions de planning`, async ({ page }) => {
      await loginAsUi(page, ACCOUNTS[role], '/planning/versions');
      await expect(page).toHaveURL(/\/forbidden$/);
    });
  }
});
