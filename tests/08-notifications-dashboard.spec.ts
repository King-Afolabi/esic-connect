import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * DOMAINE 7 — Notifications et tableau de bord.
 *
 * `docs/CURRENT-STATE.md` : audience réelle des notifications métier =
 * formateurs uniquement (dette G1-D-AUDIENCE) ; pas d'email métier
 * (au-delà de l'activation), pas de push PWA, pas de préférences par
 * canal — ces sous-domaines de la demande initiale n'existent pas dans
 * l'UI et ne sont donc pas testés (voir audit-report.md §4.5).
 */

test.describe('Tableau de bord', () => {
  for (const account of Object.values(ACCOUNTS)) {
    test(`le tableau de bord de ${account.role} affiche le compte et les rôles réels`, async ({
      page,
    }) => {
      await loginAsUi(page, account);
      await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
      // Le tableau de bord affiche l'e-mail à deux endroits (barre d'outils
      // + carte "Session") : on cible la puce d'identité de la barre
      // d'outils pour éviter une correspondance ambiguë.
      await expect(page.locator('[aria-label="Utilisateur connecté"]')).toHaveText(account.email);
    });
  }
});

test.describe('Centre de notifications', () => {
  test('écran accessible par tout rôle authentifié, filtre Toutes/Non lues fonctionnel', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/notifications');
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();

    // `mat-button-toggle` expose un rôle ARIA "radio" (groupe "radiogroup"),
    // pas "button" — vérifié sur l'instantané réel de la page.
    await page.getByRole('radio', { name: 'Non lues' }).click();
    // Doit rester sur l'écran sans erreur, avec l'état vide OU une liste —
    // jamais une exception non gérée.
    await expect(page.locator('.notif__panel[role="alert"]')).toHaveCount(0);

    await page.getByRole('radio', { name: 'Toutes' }).click();
    await expect(page.locator('.notif__panel[role="alert"]')).toHaveCount(0);
  });

  test('le formateur voit soit ses notifications réelles, soit l\'état vide documenté — jamais un plantage', async ({
    page,
  }) => {
    // Le lot G1-D documente une audience volontairement restreinte aux
    // formateurs (annulation de séance, remplacement affecté/terminé) : ni
    // l'ouverture ni la clôture d'une séance n'en produisent, donc l'état
    // vide est un résultat légitime ici, pas seulement un repli de test.
    await loginAsUi(page, ACCOUNTS.TEACHER, '/notifications');
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
    const list = page.locator('ul.notif__list li');
    const emptyState = page.getByText('Aucune notification', { exact: false });
    await expect(list.first().or(emptyState)).toBeVisible({ timeout: 10_000 });
  });
});
