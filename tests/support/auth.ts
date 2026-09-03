import { Page, expect } from '@playwright/test';
import { DemoAccount } from './accounts';

/**
 * IMPORTANT — architecture réelle observée (voir audit-report.md, finding
 * "F-ENV-2") : le jeton JWT vit uniquement dans un service Angular en
 * mémoire (`AuthService`, commentaire du fichier : « Ni localStorage ni
 * sessionStorage ni cookie écrit en JavaScript »), sans restauration au
 * démarrage. **Toute navigation "dure" (`page.goto`, un F5) efface donc la
 * session immédiatement**, quel que soit le compte.
 *
 * L'application atténue cela pour le cas nominal (lien profond, favori) :
 * `authGuard`/`roleGuard` redirigent vers `/login?redirect=<url demandée>`,
 * et l'écran de connexion navigue vers `redirect` après succès — via le
 * routeur Angular, donc SANS rechargement (`login.ts`, `router.navigateByUrl`).
 *
 * `loginAsUi` exploite ce mécanisme réel plutôt que de le contourner :
 * pour atteindre une page protégée après connexion, on y navigue AVANT
 * d'être authentifié (`page.goto(targetPath)`, navigation dure mais sans
 * session à perdre), ce qui déclenche la redirection vers `/login?redirect=…`,
 * puis on se connecte — l'application termine alors sur `targetPath` par
 * une navigation interne, sans jamais recharger le document.
 *
 * Une fois connecté, toute navigation ultérieure DOIT passer par des clics
 * sur des liens/boutons de l'application (routage interne Angular), jamais
 * par un nouveau `page.goto` — qui effacerait la session comme n'importe
 * quel rechargement réel.
 */
export async function loginAsUi(page: Page, account: DemoAccount, targetPath?: string): Promise<void> {
  await page.goto(targetPath ?? '/login');
  // Si `targetPath` est déjà accessible sans connexion (ne devrait pas
  // arriver pour une route protégée), on retombe simplement sur /login.
  if (!/\/login(\?|$)/.test(page.url())) {
    // Improbable : la page cible s'est chargée sans redirection.
    return;
  }
  await page.getByLabel('Adresse électronique').fill(account.email);
  await page.getByLabel('Mot de passe').fill(account.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  // On attend seulement la sortie de /login : la destination finale dépend
  // du rôle (targetPath, un enfant par défaut de targetPath comme
  // `/academic` → `/academic/academic-years`, ou `/forbidden` si le rôle
  // n'a pas accès) — c'est au test appelant de vérifier laquelle, pas à ce
  // helper de la présupposer.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 10_000 });
}

export async function logoutAsUi(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(page).toHaveURL(/\/login$/);
}

/**
 * Capture le jeton porteur (`Authorization: Bearer …`) réellement envoyé
 * par l'application, en observant une vraie requête XHR authentifiée —
 * seul moyen d'obtenir ce jeton depuis l'extérieur de l'application
 * puisqu'il n'est jamais exposé dans le DOM ni le stockage du navigateur
 * (RG-085). Utilisé uniquement par des tests qui doivent rejouer un appel
 * API direct avec un jeton réel (ex. reproduire un défaut d'API connu).
 */
export async function loginAndCaptureBearerToken(page: Page, account: DemoAccount): Promise<string> {
  await loginAsUi(page, account);
  const requestPromise = page.waitForRequest(
    (req) => !!req.headers()['authorization']?.startsWith('Bearer '),
  );
  // Un clic vers un écran qui appelle l'API garantit une requête
  // authentifiée observable, sans dépendre d'un appel spécifique au
  // tableau de bord (qui n'en émet pas forcément).
  // `exact: true` : la cloche de la barre d'outils porte l'aria-label
  // "Ouvrir le centre de notifications", qui contient aussi le mot
  // "notifications" et serait sinon également apparié.
  await page.getByRole('link', { name: 'Notifications', exact: true }).click();
  const request = await requestPromise;
  return request.headers()['authorization'];
}
