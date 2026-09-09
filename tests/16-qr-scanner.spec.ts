import { test, expect, Page } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * Scan QR dans l'application — parcours apprenant (`/attendance`).
 *
 * **Simule le scan sans vraie caméra** : un script d'init remplace
 * `navigator.mediaDevices.getUserMedia` par un flux issu d'un `<canvas>`
 * et pose un `BarcodeDetector` factice dont le `detect()` renvoie la
 * valeur placée dans `window.__DECODE_VALUE`. Le composant `app-qr-scanner`
 * suit alors exactement son chemin réel (boucle de décodage → analyse
 * locale → appel de l'API d'émargement existante → affichage du résultat
 * serveur). Aucune règle serveur n'est contournée : les jetons injectés
 * sont invalides, et c'est le serveur qui le dit.
 *
 * Prérequis d'exécution : pile de démonstration démarrée (`back-end`
 * profil `demo` + `ng serve`), `ESIC_DEMO_PASSWORD` (+ `ESIC_DEMO_TOTP_SECRET`)
 * exportés — cf. `docs/11-guide-deploiement.md` §5.
 *
 * **Non couvert ici, à faire en recette physique** (téléphones réels) :
 * l'ouverture effective de la caméra arrière, la lecture d'un QR imprimé,
 * le tap NFC d'un tag NDEF. Voir `docs/deployment/NFC-ROOM-TAGS.md`.
 */

/** Chaîne Base64 URL-safe ~43 caractères — forme d'un jeton / d'une référence. */
const OPAQUE = 'q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy';

async function installFakeCamera(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const w = window as unknown as { __DECODE_VALUE?: string | null };
    w.__DECODE_VALUE = null;

    const canvas = document.createElement('canvas');
    canvas.width = 320;
    canvas.height = 320;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, 320, 320);
    ctx.fillStyle = '#000000';
    ctx.fillRect(24, 24, 72, 72);
    const stream = (canvas as HTMLCanvasElement & { captureStream(fps?: number): MediaStream })
      .captureStream(12);

    const md = navigator.mediaDevices as MediaDevices;
    md.getUserMedia = async () => stream;
    md.enumerateDevices = async () => [];

    class FakeBarcodeDetector {
      static async getSupportedFormats(): Promise<string[]> {
        return ['qr_code'];
      }
      async detect(): Promise<{ rawValue: string }[]> {
        const value = (window as unknown as { __DECODE_VALUE?: string | null }).__DECODE_VALUE;
        return value ? [{ rawValue: value }] : [];
      }
    }
    (window as unknown as { BarcodeDetector: unknown }).BarcodeDetector = FakeBarcodeDetector;
  });
}

/** Place la prochaine valeur « scannée ». */
async function setDecodeValue(page: Page, value: string): Promise<void> {
  await page.evaluate((v) => {
    (window as unknown as { __DECODE_VALUE: string | null }).__DECODE_VALUE = v;
  }, value);
}

test.describe('Scan QR dans l\'application (caméra simulée)', () => {
  test.beforeEach(async ({ page }) => {
    await installFakeCamera(page);
  });

  test('le scanner ne démarre qu\'après un clic explicite', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    await expect(page.getByRole('heading', { name: 'Émargement' })).toBeVisible();
    await expect(page.locator('app-qr-scanner')).toHaveCount(0);

    await page.getByRole('button', { name: 'Scanner un QR code' }).click();
    await expect(page.locator('app-qr-scanner')).toHaveCount(1);
    await expect(page.getByText('Caméra activée')).toBeVisible();
  });

  test('un jeton dynamique invalide est refusé par le serveur, message clair', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    await page.getByRole('button', { name: 'Scanner un QR code' }).click();
    await setDecodeValue(page, OPAQUE);
    // Analyse locale → POST /attendance/validate {token} → 409 ATT_TOKEN_INVALID.
    await expect(page.getByText(/invalide ou a expiré/i)).toBeVisible({ timeout: 15_000 });
  });

  test('une URL externe est refusée sans appel réseau', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    let validateCalls = 0;
    await page.route('**/api/v1/attendance/**', (route) => {
      validateCalls += 1;
      return route.continue();
    });
    await page.getByRole('button', { name: 'Scanner un QR code' }).click();
    await setDecodeValue(page, `https://evil.example/attendance?ref=${OPAQUE}`);
    await expect(page.getByText(/n'est pas un code d'émargement ESIC Connect/)).toBeVisible({
      timeout: 15_000,
    });
    expect(validateCalls).toBe(0);
  });

  test('une URL interne /attendance?ref= route vers le parcours QR fixe de salle', async ({
    page,
  }, testInfo) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    const origin = new URL(testInfo.project.use.baseURL ?? 'http://localhost:4200').origin;
    await page.getByRole('button', { name: 'Scanner un QR code' }).click();
    await setDecodeValue(page, `${origin}/attendance?ref=${OPAQUE}`);
    // POST /attendance/room-qr {roomReference} → 404 ATT_ROOM_QR_UNKNOWN (réf. inconnue).
    await expect(page.getByText(/n'est pas reconnu/i)).toBeVisible({ timeout: 15_000 });
  });

  test('le repli « saisir un code court » ferme le scanner et rend la main au formulaire', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    await page.getByRole('button', { name: 'Scanner un QR code' }).click();
    await page.getByRole('button', { name: 'Saisir un code court à la place' }).click();
    await expect(page.locator('app-qr-scanner')).toHaveCount(0);
    await expect(page.getByLabel('Code court')).toBeFocused();
  });

  test('un lien profond ?ref= pré-remplit le champ de salle après authentification', async ({
    page,
  }) => {
    const ref = OPAQUE;
    await loginAsUi(page, ACCOUNTS.STUDENT, `/attendance?ref=${ref}`);
    await expect(page).toHaveURL(new RegExp(`/attendance\\?ref=${ref}`));
    await expect(page.getByText(/rien n'est envoyé/)).toBeVisible();
    await expect(page.getByLabel('Code du QR de salle')).toHaveValue(ref);
  });
});
