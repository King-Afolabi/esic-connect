import path from 'node:path';
import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi, logoutAsUi } from './support/auth';

const CAPTURES = path.join(__dirname, '..', 'captures');

/**
 * DOMAINE 1 — Authentification.
 *
 * Périmètre réel de l'application (`frontend/src/app/app.routes.ts`,
 * `core/guards/*`) : connexion email + mot de passe → JWT gardé en
 * mémoire. `docs/CURRENT-STATE.md` classe explicitement en
 * `HORS_PÉRIMÈTRE_ASSUMÉ` : mot de passe oublié, MFA, WebAuthn, Turnstile,
 * logout serveur / révocation de session, timeout de session mesurable
 * (30 min — trop long pour un test E2E, non simulé ici). Ces sous-domaines
 * ne sont donc PAS testés ci-dessous ; voir audit-report.md §4.5.
 */

test.describe('Connexion — comptes réels par rôle', () => {
  for (const account of Object.values(ACCOUNTS)) {
    test(`connexion réussie : ${account.role} (${account.email})`, async ({ page }) => {
      await loginAsUi(page, account);
      // Le tableau de bord affiche aussi l'e-mail (carte "Session") : on
      // cible explicitement la puce d'identité de la barre d'outils
      // (`app-shell.html`, `aria-label="Utilisateur connecté"`) pour éviter
      // une correspondance ambiguë entre les deux occurrences.
      await expect(page.locator('[aria-label="Utilisateur connecté"]')).toHaveText(account.email);
      // Les rôles réellement détenus doivent apparaître comme puces dans
      // l'en-tête (app-shell.html) — vérifie que le JWT porte bien les
      // rôles attendus, pas seulement que la connexion a réussi.
      for (const role of account.roles) {
        // Les libellés sont traduits (roleLabel()) ; on vérifie la
        // présence d'au moins une puce de rôle plutôt que le code brut.
        await expect(page.locator('.shell__role-chip')).not.toHaveCount(0);
      }
    });
  }

  test('compte multi-rôles : les deux puces de rôle sont visibles', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER);
    const chips = page.locator('.shell__role-chip');
    await expect(chips).toHaveCount(2);
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '00-connexion-multi-roles.png'),
      fullPage: true,
    });
  });
});

test.describe('Connexion — cas d\'erreur (AC-001)', () => {
  test('mot de passe incorrect : message générique, pas de redirection', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Adresse électronique').fill(ACCOUNTS.STUDENT.email);
    await page.getByLabel('Mot de passe').fill('MauvaisMotDePasse123!');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    // Le conteneur `role="alert"` existe en permanence dans le DOM
    // (`login.html`) : on attend un contenu non vide, pas la seule
    // présence du conteneur.
    await expect(page.getByRole('alert')).not.toBeEmpty({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test('adresse inconnue : même message générique (pas de divulgation de compte)', async ({
    page,
  }) => {
    // Le conteneur `role="alert"` est TOUJOURS présent dans le DOM (seul
    // son contenu est conditionnel, `login.html`) : attendre sa seule
    // visibilité ne garantit pas que la réponse du serveur est arrivée.
    // On attend explicitement la réponse de l'appel de connexion.
    await page.goto('/login');
    await page.getByLabel('Adresse électronique').fill('inconnu.e2e@example.test');
    await page.getByLabel('Mot de passe').fill('QuelconquePassword123!');
    const firstAttempt = page.waitForResponse('**/api/v1/auth/login');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await firstAttempt;
    await expect(page.getByRole('alert')).not.toBeEmpty({ timeout: 10_000 });
    const unknownEmailError = (await page.getByRole('alert').textContent())?.trim();
    expect(unknownEmailError).not.toBe('');

    await page.getByLabel('Adresse électronique').fill(ACCOUNTS.STUDENT.email);
    await page.getByLabel('Mot de passe').fill('QuelconquePassword123!');
    const secondAttempt = page.waitForResponse('**/api/v1/auth/login');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await secondAttempt;
    await expect(page.getByRole('alert')).not.toBeEmpty({ timeout: 10_000 });
    const wrongPasswordError = (await page.getByRole('alert').textContent())?.trim();

    // AC-001 : le message ne doit pas permettre de distinguer un compte
    // inexistant d'un mot de passe erroné sur un compte réel.
    expect(unknownEmailError).toBe(wrongPasswordError);
  });

  test('champs vides : validation cliente bloque la soumission (aucune requête)', async ({
    page,
  }) => {
    let loginCalled = false;
    await page.route('**/api/v1/auth/login', (route) => {
      loginCalled = true;
      route.continue();
    });
    await page.goto('/login');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page.getByText("L'adresse électronique est obligatoire.")).toBeVisible();
    await expect(page.getByText('Le mot de passe est obligatoire.')).toBeVisible();
    expect(loginCalled).toBe(false);
  });

  test("email au format invalide : message de validation dédié", async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Adresse électronique').fill('pas-un-email');
    await page.getByLabel('Mot de passe').fill('x');
    await page.getByLabel('Adresse électronique').blur();
    await expect(page.getByText('Saisissez une adresse électronique valide.')).toBeVisible();
  });
});

test.describe('Gardes de navigation', () => {
  test('visiteur non authentifié sur une route protégée → redirigé vers /login', async ({
    page,
  }) => {
    await page.goto('/students');
    await expect(page).toHaveURL(/\/login/);
  });

  test('utilisateur déjà authentifié revenant sur /login (navigation interne) → redirigé vers /dashboard (guestGuard)', async ({
    page,
  }) => {
    // `page.goto('/login')` est une navigation DURE : elle effacerait la
    // session en mémoire avant même que le garde ne s'exécute (ce serait
    // alors "visiteur non authentifié", pas le cas testé ici). On revient
    // plutôt sur l'entrée d'historique /login déjà créée par `loginAsUi`
    // via `goBack()` — une navigation interne (popstate) que le routeur
    // Angular intercepte sans recharger le document, donc sans perdre le
    // jeton en mémoire.
    await loginAsUi(page, ACCOUNTS.ADMIN);
    await page.goBack();
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('déconnexion : retour à /login, la page protégée redemande une connexion', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN);
    await logoutAsUi(page);
    // Le jeton est en mémoire (jamais localStorage, RG-085) : un rechargement
    // de page perd donc la session — comportement attendu, pas un bug.
    await page.goto('/students');
    await expect(page).toHaveURL(/\/login/);
  });
});
