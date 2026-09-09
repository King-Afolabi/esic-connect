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
 * mémoire. Le second facteur ADMIN/SUPER_ADMIN (RG-007) est désormais
 * franchi réellement par `loginAsUi` (`tests/support/auth.ts`, dette
 * T-19/T-20) : chaque connexion `ACCOUNTS.ADMIN`/`ACCOUNTS.SUPER_ADMIN`
 * ci-dessous passe par le VRAI défi `/connexion/verification`, pas par un
 * contournement. `docs/STATUS.md` recense parmi les limites connues : mot de passe oublié, WebAuthn, Turnstile,
 * logout serveur / révocation de session, timeout de session mesurable
 * (30 min — trop long pour un test E2E, non simulé ici). Ces sous-domaines
 * ne sont donc PAS testés ci-dessous ; voir docs/09-strategie-tests.md
 */

test.describe('Connexion — comptes réels par rôle', () => {
  for (const account of Object.values(ACCOUNTS)) {
    test(`connexion réussie : ${account.role} (${account.email})`, async ({ page }) => {
      await loginAsUi(page, account);
      // L'identité connectée est portée par le déclencheur du panneau
      // Profil de la barre d'outils (`profile-menu.html`,
      // `aria-label="Profil — <email>"`) depuis la refonte « profil en
      // icône seule » (le nom court n'est plus affiché, l'adresse reste sur l'aria-label).
      await expect(page.locator('button.profile-menu__trigger')).toHaveAttribute(
        'aria-label',
        `Profil — ${account.email}`,
      );
      // Les rôles réellement détenus apparaissent comme puces dans le
      // panneau Profil (déplacées de la barre d'outils vers le panneau) :
      // ouvrir le menu et vérifier au moins une puce — confirme que le JWT
      // porte bien des rôles, pas seulement que la connexion a réussi.
      await page.locator('button.profile-menu__trigger').click();
      await expect(page.locator('.profile-menu__roles .esic-badge')).not.toHaveCount(0);
      await page.keyboard.press('Escape');
    });
  }

  test('compte multi-rôles : les deux puces de rôle sont visibles', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER);
    // Les puces de rôle vivent désormais dans le panneau Profil
    // (`profile-menu.html`, `.profile-menu__roles .esic-badge`) et non
    // plus dans la barre d'outils.
    await page.locator('button.profile-menu__trigger').click();
    const chips = page.locator('.profile-menu__roles .esic-badge');
    await expect(chips).toHaveCount(2);
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '00-connexion-multi-roles.png'),
      fullPage: true,
    });
    await page.keyboard.press('Escape');
  });
});

test.describe('Connexion — cas d\'erreur (AC-001)', () => {
  test('mot de passe incorrect : message générique, pas de redirection', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Adresse électronique').fill(ACCOUNTS.STUDENT.email);
    await page.getByLabel('Mot de passe').fill('MauvaisMotDePasse123!');
    await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
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
    await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
    await firstAttempt;
    await expect(page.getByRole('alert')).not.toBeEmpty({ timeout: 10_000 });
    const unknownEmailError = (await page.getByRole('alert').textContent())?.trim();
    expect(unknownEmailError).not.toBe('');

    await page.getByLabel('Adresse électronique').fill(ACCOUNTS.STUDENT.email);
    await page.getByLabel('Mot de passe').fill('QuelconquePassword123!');
    const secondAttempt = page.waitForResponse('**/api/v1/auth/login');
    await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
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
    await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
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
    //
    // Compte SANS second facteur obligatoire (RG-007) : un compte
    // ADMIN/SUPER_ADMIN insère une entrée d'historique supplémentaire
    // (`/connexion/verification`) entre `/login` et `/dashboard`, ce qui
    // romprait l'hypothèse « un seul `goBack()` ramène à /login » — sans
    // rapport avec ce que ce test vérifie (le garde `guestGuard`).
    await loginAsUi(page, ACCOUNTS.STUDENT);
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
