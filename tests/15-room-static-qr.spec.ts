import path from 'node:path';
import { test, expect, Page } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

const CAPTURES = path.join(__dirname, '..', 'captures');

/**
 * EF-ORG-003 — Gestion et impression du QR fixe permanent d'une salle.
 *
 * Couvre l'écran (le référentiel métier est déjà couvert côté serveur :
 * `RoomStaticQrAdminIntegrationTests`, `RoomQrAttendanceIntegrationTests`) :
 *
 * 1. un ADMIN ouvre une fiche de site, consulte le QR fixe d'une salle,
 *    l'émet si besoin, ouvre l'affiche imprimable, puis le renouvelle
 *    après une confirmation explicite ;
 * 2. un SUPER_ADMIN consulte et imprime, mais ne voit **jamais** l'action
 *    de renouvellement (réservée à ADMIN — vérifié aussi côté serveur par
 *    un `403`).
 *
 * Prérequis d'exécution : pile de démonstration démarrée
 * (`back-end` profil `demo` + `ng serve`), `ESIC_DEMO_PASSWORD` et
 * `ESIC_DEMO_TOTP_SECRET` exportés — cf. `docs/11-guide-deploiement.md` §5.
 */

async function openFirstSiteRooms(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Sites', exact: true })).toBeVisible({
    timeout: 15_000,
  });
  // La ligne de site n'est plus cliquable : la fiche s'ouvre via le lien
  // « Consulter » de la colonne d'actions (routing interne Angular, la
  // session est conservée).
  await page.getByRole('link', { name: /^Consulter le site / }).first().click();
  await expect(page.getByRole('heading', { name: 'Salles', exact: true })).toBeVisible({
    timeout: 15_000,
  });
}

async function openRoomQrPanel(page: Page): Promise<void> {
  const show = page
    .getByRole('button', { name: /^Afficher le QR fixe de la salle/ })
    .first();
  await expect(show).toBeVisible({ timeout: 15_000 });
  await show.click();
  await expect(page.getByText(/^QR fixe — /)).toBeVisible({ timeout: 15_000 });
}

test.describe('EF-ORG-003 — QR fixe permanent de salle', () => {
  test('ADMIN : consulter, émettre, imprimer et renouveler (avec confirmation)', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites');
    await openFirstSiteRooms(page);
    await openRoomQrPanel(page);

    // Émettre le QR s'il n'existe pas encore pour cette salle.
    const issue = page.getByRole('button', { name: 'Émettre le QR fixe' });
    if (await issue.isVisible().catch(() => false)) {
      await issue.click();
    }

    // Le panneau montre une référence support MASQUÉE (jamais la valeur
    // complète) et propose l'impression.
    await expect(page.getByText(/réf\. support .+…/)).toBeVisible({ timeout: 15_000 });
    const printLink = page.getByRole('link', { name: "Imprimer l'affiche" });
    await expect(printLink).toBeVisible();
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '15-qr-fixe-panneau-admin.png'),
      fullPage: true,
    });

    // Affiche imprimable.
    await printLink.click();
    await expect(page).toHaveURL(/\/rooms\/[^/]+\/qr-poster$/);
    await expect(page.getByText('ESIC Connect — Émargement')).toBeVisible();
    await expect(page.getByText('Ne pas déplacer cette affiche')).toBeVisible();
    await expect(page.locator('qrcode img')).toBeVisible();
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '15-qr-fixe-affiche.png'),
      fullPage: true,
    });

    // Retour à la fiche (lien interne), puis renouvellement.
    await page.getByRole('link', { name: 'Retour au site' }).click();
    await openRoomQrPanel(page);
    const before = (await page.getByText(/réf\. support .+…/).innerText()).trim();

    await page.getByRole('button', { name: /^Renouveler le QR/ }).click();
    await expect(page.getByText('immédiatement invalides')).toBeVisible();
    await page.getByRole('button', { name: 'Renouveler et invalider les affiches' }).click();

    // La référence support masquée change.
    await expect(page.getByText(/réf\. support .+…/)).not.toHaveText(before, { timeout: 15_000 });
  });

  test('SUPER_ADMIN : consultation et impression, mais aucun renouvellement', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.SUPER_ADMIN, '/organization/sites');
    await openFirstSiteRooms(page);
    await openRoomQrPanel(page);

    // Jamais d'action de renouvellement / d'émission pour ce rôle.
    await expect(page.getByRole('button', { name: /^Renouveler le QR/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Émettre le QR fixe' })).toHaveCount(0);

    // S'il existe un QR pour cette salle, l'impression reste ouverte.
    const printLink = page.getByRole('link', { name: "Imprimer l'affiche" });
    if (await printLink.isVisible().catch(() => false)) {
      await printLink.click();
      await expect(page.getByText('ESIC Connect — Émargement')).toBeVisible();
    }
  });
});
