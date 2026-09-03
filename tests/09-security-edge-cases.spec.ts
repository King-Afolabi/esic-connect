import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi, loginAndCaptureBearerToken } from './support/auth';

/**
 * DOMAINE 8 — Cas limites et sécurité.
 *
 * Hors périmètre car sans surface réelle dans l'application (aucun test
 * inventé pour ces sous-domaines, voir audit-report.md §4.5) : CSRF (JWT
 * porté par l'en-tête `Authorization`, pas par cookie de session —
 * `allowCredentials=false`, documenté dans `application.yml`, donc la
 * classe d'attaque ne s'applique pas telle quelle), upload de justificatif
 * (écran non atteignable dans ce jeu de rôles démo), race condition de
 * double édition concurrente d'une même ressource par deux onglets.
 */

test.describe('XSS — contenu utilisateur affiché comme texte, jamais exécuté', () => {
  test('un nom de site contenant une charge XSS ne s\'exécute pas', async ({ page }) => {
    await page.addInitScript(() => {
      (window as unknown as { __xssFired?: boolean }).__xssFired = false;
    });
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites/new');
    const payload = '<img src=x onerror="window.__xssFired=true">';
    await page.getByLabel('Code', { exact: true }).fill(`E2E-XSS-${Date.now()}`);
    await page.getByLabel('Nom').fill(payload);
    await page.getByLabel('Fuseau horaire (IANA)').fill('Europe/Paris');
    await page.getByRole('button', { name: 'Créer le site' }).click();
    await expect(page).toHaveURL(/\/organization\/sites\/[0-9a-f-]+$/, { timeout: 10_000 });

    const fired = await page.evaluate(
      () => (window as unknown as { __xssFired?: boolean }).__xssFired,
    );
    expect(fired).toBe(false);
    await expect(page.locator('img[src="x"]')).toHaveCount(0);
  });
});

test.describe('Validation cliente — champs obligatoires, longueurs', () => {
  test('formulaire de site vide : bouton bloqué par la validation, aucune requête réseau', async ({
    page,
  }) => {
    let called = false;
    await page.route('**/api/v1/sites', (route) => {
      called = true;
      route.continue();
    });
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites/new');
    await page.getByRole('button', { name: 'Créer le site' }).click();
    expect(called).toBe(false);
    await expect(page).toHaveURL(/\/organization\/sites\/new$/);
  });

  test('nom de site : une chaîne très longue est tronquée à la limite déclarée (150)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites/new');
    const longName = 'A'.repeat(400);
    await page.getByLabel('Nom').fill(longName);
    const actual = await page.getByLabel('Nom').inputValue();
    expect(actual.length).toBeLessThanOrEqual(150);
  });
});

test.describe('Double soumission', () => {
  test('deux clics rapides sur "Créer le site" ne créent qu\'un seul site', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/organization/sites/new');
    const code = `E2E-DBL-${Date.now()}`;
    await page.getByLabel('Code', { exact: true }).fill(code);
    await page.getByLabel('Nom').fill('Double soumission');
    await page.getByLabel('Fuseau horaire (IANA)').fill('Europe/Paris');

    // La requête réelle est ralentie artificiellement pour donner à un
    // second clic une fenêtre réaliste pendant laquelle le premier est
    // encore en vol — sans ce délai, la navigation qui suit le premier
    // clic est trop rapide pour qu'un second clic distinct ait un sens
    // (le bouton a déjà quitté le DOM).
    await page.route('**/api/v1/sites', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 800));
      await route.continue();
    });
    const button = page.getByRole('button', { name: 'Créer le site' });
    await button.click();
    // Le formulaire désactive le bouton pendant la soumission
    // (`[disabled]="submitting()"`, `site-form.html`) : un second clic
    // pendant cette fenêtre doit être un no-op, pas une seconde création.
    await expect(button).toBeDisabled();
    await button.click({ force: true }).catch(() => {});
    await expect(page).toHaveURL(/\/organization\/sites\/[0-9a-f-]+$/, { timeout: 10_000 });

    // Navigation interne (onglet "Sites" du fil d'Ariane de l'écran) pour
    // rester dans la même session authentifiée.
    await page.getByRole('link', { name: 'Sites' }).click();
    await expect(page).toHaveURL(/\/organization\/sites$/);
    await page.getByLabel('Code ou nom').fill(code);
    await page.getByRole('button', { name: 'Filtrer' }).click();
    await expect(page.locator('table tbody tr')).toHaveCount(1);
  });
});

test.describe('IDOR / contrôle serveur direct (pas seulement côté client)', () => {
  test('un appel API sans jeton reçoit 401, jamais les données', async ({ page }) => {
    const response = await page.request.get('http://localhost:8080/api/v1/users');
    expect(response.status()).toBe(401);
  });

  test('un jeton falsifié est rejeté par le serveur (signature vérifiée)', async ({ page }) => {
    const response = await page.request.get('http://localhost:8080/api/v1/users', {
      headers: { Authorization: 'Bearer ceci.nest.pasunjwt' },
    });
    expect(response.status()).toBe(401);
  });

  test('un STUDENT authentifié est refusé sur une route réservée ADMIN (navigation)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/administration');
    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('un STUDENT authentifié rejouant son propre jeton contre une API réservée ADMIN reçoit 403 (pas 500, pas de fuite)', async ({
    page,
  }) => {
    // Le jeton JWT vit en mémoire dans le service Angular (RG-085 : jamais
    // dans le DOM ni `localStorage`) : capturé ici depuis une vraie requête
    // XHR de l'application, puis rejoué directement contre une route que
    // l'UI ne laisse jamais atteindre pour ce rôle.
    const token = await loginAndCaptureBearerToken(page, ACCOUNTS.STUDENT);
    const response = await page.request.get('http://localhost:8080/api/v1/users', {
      headers: { Authorization: token },
    });
    expect(response.status()).toBe(403);
  });
});

test.describe('Défaut d\'API déjà tracé — reconfirmation en direct (pas une découverte)', () => {
  test('GET /api/v1/planning/versions sans classGroupPublicId renvoie 500 au lieu de 400 (docs/reports/DEMO_CRITICAL_PATH_DIAGNOSTIC.md §2)', async ({
    page,
  }) => {
    // Non atteignable depuis l'écran réel (le sélecteur de classe est
    // obligatoire avant tout appel) : reproduit ici avec un jeton ADMIN
    // réel, comme le ferait tout autre client de cette route documentée
    // (OpenAPI) — un appel sans jeton renverrait 401 et ne testerait rien.
    const token = await loginAndCaptureBearerToken(page, ACCOUNTS.ADMIN);
    const response = await page.request.get('http://localhost:8080/api/v1/planning/versions', {
      headers: { Authorization: token },
    });
    // CORRIGÉ le 3 septembre 2026 : `GlobalExceptionHandler` traite
    // désormais `MissingServletRequestParameterException` en 400
    // VALIDATION_ERROR. Ce test est le garde-fou de non-régression.
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.code).toBe('VALIDATION_ERROR');
  });
});
