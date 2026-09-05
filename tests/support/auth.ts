import { Page, expect, test } from '@playwright/test';
import { DemoAccount } from './accounts';
import { claimTotpCode } from './totp';

/**
 * IMPORTANT — architecture réelle observée : le jeton JWT vit uniquement dans un service Angular en
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
  // `exact: true` est INDISPENSABLE depuis le sprint 2 : l'écran de
  // connexion porte aussi un bouton « Se connecter avec une clé d'accès »
  // (passkey, EF-AUTH-007), que le libellé non exact apparie également.
  // Sans cela, Playwright échoue en « strict mode violation » et TOUTE la
  // suite navigateur tombe dès l'authentification.
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  // On attend la sortie de /login : soit directement vers la destination
  // finale (rôle sans second facteur obligatoire), soit vers l'écran de
  // second facteur `/connexion/verification` (ADMIN / SUPER_ADMIN,
  // RG-007) — c'est ce dernier cas que `resolveMfaChallengeIfPresent`
  // franchit ci-dessous.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 10_000 });
  await resolveMfaChallengeIfPresent(page, account);
  // La destination finale dépend du rôle (targetPath, un enfant par défaut
  // de targetPath comme `/academic` → `/academic/academic-years`, ou
  // `/forbidden` si le rôle n'a pas accès) — c'est au test appelant de
  // vérifier laquelle, pas à ce helper de la présupposer.
}

/**
 * Franchit l'écran de second facteur (`/connexion/verification`) quand la
 * connexion vient d'y aboutir — un compte `ADMIN` ou `SUPER_ADMIN`
 * (RG-007 : second facteur obligatoire) n'obtient jamais de jeton contre
 * son seul mot de passe (`DEC-S2-005`). Ne contourne aucun contrôle
 * serveur : franchit le VRAI parcours HTTP `/mfa/verify` ou
 * `/mfa/enroll` + `/mfa/enroll/confirm`, code TOTP calculé localement
 * (dette T-19/T-20, `docs/CURRENT-STATE.md`).
 *
 * - **VERIFY** (facteur déjà actif) : le secret n'est jamais affiché à
 *   l'écran — il faut le connaître à l'avance. Utilise
 *   `account.totpSecret` (`ESIC_DEMO_TOTP_SECRET`), lui-même aligné sur le
 *   facteur déterministe activé côté serveur par `DemoDataInitializer`.
 * - **ENROLL** (aucun facteur actif — ex. `ESIC_DEMO_TOTP_SECRET` non
 *   défini côté back-end) : le secret est généré aléatoirement par le
 *   serveur et affiché en clair à l'écran (`.mfa__secret code`) — il est
 *   lu là, jamais depuis l'environnement, qui ne le connaît pas.
 */
async function resolveMfaChallengeIfPresent(page: Page, account: DemoAccount): Promise<void> {
  if (!/\/connexion\/verification(\?|$)/.test(page.url())) {
    return; // Rôle sans second facteur obligatoire : rien à franchir.
  }

  // Le titre distingue immédiatement les deux parcours (connu dès la
  // navigation, sans attendre un appel réseau) — plus fiable qu'une
  // course entre deux sélecteurs asynchrones.
  const isEnrolling = await page
    .getByText('Ajoutez votre second facteur', { exact: false })
    .isVisible()
    .catch(() => false);

  let secret: string | undefined;
  if (isEnrolling) {
    const secretLocator = page.locator('.mfa__secret code');
    await secretLocator.waitFor({ state: 'visible', timeout: 10_000 });
    secret = (await secretLocator.textContent())?.trim();
  } else {
    secret = account.totpSecret;
  }
  if (!secret) {
    throw new Error(
      `Second facteur requis pour ${account.email} mais aucun secret TOTP disponible : ` +
        'définissez ESIC_DEMO_TOTP_SECRET (même valeur que le back-end, profil demo) ' +
        'avant de lancer la suite. Voir docs/CURRENT-STATE.md, dette T-19/T-20.',
    );
  }

  await submitTotpCode(page, secret, isEnrolling);

  await page.waitForURL((url) => !url.pathname.startsWith('/connexion/verification'), {
    timeout: 10_000,
  });
}

/**
 * Saisit un code TOTP et soumet le formulaire, avec une marge de sécurité
 * contre l'anti-rejeu réel (RG-054/055) : `claimTotpCode` réserve
 * localement un pas jamais soumis PAR CE PROCESSUS pour ce secret (les
 * comptes ADMIN/SUPER_ADMIN sont partagés par toute la suite,
 * `workers: 1`), mais cette réservation en mémoire ne survit pas à un
 * redémarrage du worker Playwright après l'échec d'un test précédent —
 * document Playwright : un test en échec fait repartir le worker suivant
 * de zéro. Si le serveur rejette malgré tout (pas déjà consommé par un
 * worker antérieur, ou par `scripts/seed-demo.sh` lancé juste avant), on
 * retente UNE fois avec un nouveau pas réservé — jamais un contournement,
 * seulement l'attente réelle et bornée (≤ 30 s) que la protection impose.
 * Le timeout du test en cours est prolongé d'autant plutôt que dissimulé.
 */
async function submitTotpCode(page: Page, secret: string, isEnrolling: boolean): Promise<void> {
  const codeInput = page.getByLabel('Code de vérification');
  const errorAlert = page.locator('p.auth-page__error[role="alert"]');
  const successLocator = isEnrolling
    ? page.getByRole('button', { name: "J'ai noté mes codes, continuer" })
    : page.locator('app-mfa-challenge');
  const successState = isEnrolling ? 'visible' : 'detached';
  const maxAttempts = 2;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const code = await claimTotpCode(secret, async (ms) => {
      test.setTimeout(test.info().timeout + ms + 5_000);
      await page.waitForTimeout(ms);
    });

    await codeInput.waitFor({ state: 'visible', timeout: 10_000 });
    await codeInput.fill(code);
    await page.getByRole('button', { name: 'Valider', exact: true }).click();

    const outcome = await Promise.race([
      successLocator
        .first()
        .waitFor({ state: successState, timeout: 10_000 })
        .then((): 'success' => 'success')
        .catch((): 'timeout' => 'timeout'),
      errorAlert
        .filter({ hasText: /.+/ })
        .waitFor({ state: 'visible', timeout: 10_000 })
        .then((): 'error' => 'error')
        .catch((): 'timeout' => 'timeout'),
    ]);

    if (outcome === 'success') {
      if (isEnrolling) {
        // L'enrôlement affiche les codes de récupération et attend un clic
        // explicite avant de rediriger (mfa-challenge.ts: `finish()`
        // n'est appelé que par ce bouton, jamais automatiquement après
        // confirm()).
        await successLocator.click();
      }
      return;
    }
    if (attempt < maxAttempts) {
      continue; // Le prochain `claimTotpCode` réservera un pas plus récent.
    }
    const detail = await errorAlert.textContent().catch(() => null);
    throw new Error(`Échec de vérification du second facteur (tentative ${attempt}) : ${detail ?? 'inconnu'}`);
  }
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
