import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * DOMAINE 4 — Suivi d'assiduité et rapports.
 *
 * `docs/CURRENT-STATE.md` (sprint 11, EF-REP-004/005) : les exports CSV,
 * Excel et PDF sont livrés et partagent le même bouton par format
 * (`attendance-report.html`, `REPORT_EXPORT_FORMATS`) — vérifié ci-dessous
 * sur l'écran réel, pas seulement supposé absent comme le disait une
 * version antérieure de ce fichier.
 */

test.describe('Écrans de suivi d\'assiduité (ADMIN / PEDAGOGICAL_MANAGER)', () => {
  test('la synthèse et les 3 sous-rapports + la file de justificatifs sont accessibles', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/attendance-management');
    await expect(page).toHaveURL(/\/attendance-management\/summary$/);
    await expect(page.getByRole('heading', { name: "Suivi d'assiduité" })).toBeVisible();

    for (const [link, path] of [
      ['Par séance', 'sessions'],
      ['Par classe', 'classes'],
      ['Par apprenant', 'students'],
      ['Justificatifs', 'justifications'],
    ] as const) {
      await page.getByRole('link', { name: link }).click();
      await expect(page).toHaveURL(new RegExp(`/attendance-management/${path}$`));
      await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
    }
  });

  test('un PEDAGOGICAL_MANAGER a accès (périmètre serveur, pas un simple masquage)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/attendance-management/students');
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
  });
});

test.describe('Autorisations', () => {
  for (const role of ['TEACHER', 'STUDENT'] as const) {
    test(`${role} n'a pas accès au suivi d'assiduité global (consulte ses propres séances/présences ailleurs)`, async ({
      page,
    }) => {
      await loginAsUi(page, ACCOUNTS[role], '/attendance-management');
      await expect(page).toHaveURL(/\/forbidden$/);
    });
  }
});

test.describe('Export des rapports (EF-REP-003/004/005)', () => {
  test('les trois formats sont proposés et le bouton CSV déclenche un vrai téléchargement', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/attendance-management/students');
    await expect(
      page.getByRole('button', { name: 'Exporter le rapport au format CSV' }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Exporter le rapport au format Excel' }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Exporter le rapport au format PDF' }),
    ).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Exporter le rapport au format CSV' }).click();
    const download = await downloadPromise;
    // Nom réel imposé par le serveur (`Content-Disposition`,
    // `AttendanceReportController.render`) : seule l'extension est stable
    // depuis l'écran, le préfixe et les dates ne le sont pas.
    expect(download.suggestedFilename()).toMatch(/\.csv$/);
  });
});
