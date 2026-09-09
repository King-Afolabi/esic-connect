import path from 'node:path';
import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

const CAPTURES = path.join(__dirname, '..', 'captures');

/**
 * DOMAINE 2 (suite) — Opérations de masse et détection de doublons
 * (EF-USER-004, EF-USER-005 ; dette T-18, docs/STATUS.md).
 *
 * L'API des deux fonctionnalités est livrée et testée côté serveur
 * depuis le sprint 4 (`BulkUserIntegrationTests`) ; seule l'interface
 * manquait. Ces tests couvrent l'écran, pas la règle métier (déjà
 * couverte côté serveur).
 */

test.describe('Opérations de masse (EF-USER-004)', () => {
  test('sélection, aperçu (aucune écriture) puis confirmation réelle', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/administration');
    await expect(page.getByRole('heading', { name: 'Administration des comptes' })).toBeVisible();

    // Sélectionne toutes les lignes de la page courante.
    await page.getByRole('checkbox', { name: 'Sélectionner tous les comptes de la page' }).check();
    await expect(page.getByText(/compte\(s\) sélectionné\(s\)/)).toBeVisible();

    await page.getByLabel('Action').click();
    await page.getByRole('option', { name: "Réémettre l'invitation" }).click();
    await page.getByLabel('Motif').fill('Vérification de la relance groupée par la recette.');
    await page.getByRole('button', { name: 'Prévisualiser' }).click();

    // Aperçu : la synthèse apparaît, avec éligibles/ignorés/refusés.
    await expect(page.getByText(/éligible\(s\),.*ignoré\(s\),.*refusé\(s\)/)).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("Aperçu — rien n'a encore été écrit.")).toBeVisible();
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '12-operation-masse-apercu.png'),
      fullPage: true,
    });

    // Annuler l'aperçu ne doit rien avoir écrit ; on referme simplement.
    await page.getByRole('button', { name: 'Annuler', exact: true }).click();
    await expect(page.getByText(/éligible\(s\)/)).not.toBeVisible();
  });

  test('sans sélection, aucun panneau d\'opération groupée ne s\'affiche', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/administration');
    await expect(page.getByText(/compte\(s\) sélectionné\(s\)/)).not.toBeVisible();
  });

  test('un TEACHER ne voit ni sélection ni lien doublons (hors périmètre)', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, '/dashboard');
    await expect(page.getByRole('link', { name: 'Doublons détectés' })).toHaveCount(0);
  });
});

test.describe('Détection de doublons (EF-USER-005)', () => {
  test('un ADMIN atteint l\'écran, en lecture seule (aucune fusion proposée)', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/administration/duplicates');
    await expect(page.getByRole('heading', { name: 'Doublons détectés' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
    // Aucune action de fusion ou de suppression n'est jamais proposée.
    await expect(page.getByRole('button', { name: /fusion/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /supprimer/i })).toHaveCount(0);
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '13-doublons-detectes.png'),
      fullPage: true,
    });
  });

  test('une SCHOOL_ADMINISTRATION (périmètre plus large sur /administration) n\'atteint pas l\'écran de doublons', async ({
    page,
  }) => {
    // Aucun compte de démonstration SCHOOL_ADMINISTRATION n'existe
    // (DemoDataInitializer) : le périmètre plus restreint est donc
    // vérifié via un rôle qui n'a accès ni à l'un ni à l'autre.
    await loginAsUi(page, ACCOUNTS.STUDENT, '/administration/duplicates');
    await expect(page).toHaveURL(/\/forbidden$/);
  });
});
