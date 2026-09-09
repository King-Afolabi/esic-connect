import { test, expect } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * DOMAINE 11 — Pilotage et restitution (sprint 11).
 *
 * Couvre les écrans réellement livrés : recherche globale
 * (EF-USER-009), attestations d'assiduité (EF-REP-006, AC-033),
 * abonnement iCalendar (EF-INT-001, AC-034), piste d'audit (EF-AUD-002),
 * rapport des invitations non activées (EF-REP-010) et tableau
 * équivalent des graphiques (EF-REP-008).
 *
 * Rappel du support d'authentification : après connexion, on ne navigue
 * QUE par clic sur un lien de l'application — un `page.goto` recharge le
 * document et efface la session (le jeton ne vit qu'en mémoire, RG-093).
 *
 * **Comptes utilisés, et pourquoi.** Depuis le sprint 2 (RG-007,
 * `DEC-S2-005`), `ADMIN` et `SUPER_ADMIN` n'obtiennent JAMAIS de jeton
 * contre leur seul mot de passe : la connexion renvoie un défi de second
 * facteur (`ENROLL` tant qu'aucun facteur n'est enrôlé). Le support e2e
 * (`tests/support/auth.ts`) ne sait pas franchir ce défi. Les parcours
 * ci-dessous s'appuient donc sur `PEDAGOGICAL_MANAGER_TEACHER`,
 * `TEACHER` et `STUDENT`, qui reçoivent un jeton directement — ce qui
 * couvre tous les écrans du sprint 11 **sauf la consultation de la piste
 * d'audit**, réservée à `ADMIN` / `SUPER_ADMIN`. Ce qui est vérifiable
 * pour elle sans second facteur, et qui l'est ici, c'est son **refus**
 * aux autres rôles.
 */

test.describe('Recherche globale (EF-USER-009)', () => {
  test("le responsable pédagogique atteint l'écran et voit la mention de périmètre", async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/recherche');
    await expect(page.getByRole('heading', { name: 'Recherche globale' })).toBeVisible();
    await expect(page.getByText("L'adresse électronique n'est pas un critère")).toBeVisible();
  });

  test('un fragment trop court ne lance aucune recherche et le dit', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/recherche');
    await page.getByLabel('Rechercher').fill('a');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByText('Saisissez au moins 2 caractères.')).toBeVisible();
  });

  test('une recherche valide rend une liste ou un état vide, jamais une erreur', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/recherche');
    await page.getByLabel('Rechercher').fill('demo');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    const table = page.locator('table.search__table');
    const empty = page.getByText('Aucun résultat.');
    await expect(table.or(empty)).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.search__panel[role="alert"]')).toHaveCount(0);
  });

  test("un apprenant n'atteint pas l'écran (garde de rôle + serveur)", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/recherche');
    // Le garde de route redirige vers /forbidden ; l'API refuserait de
    // toute façon (403). L'un ou l'autre est acceptable, l'écran de
    // recherche ne doit simplement jamais s'afficher.
    await expect(page.getByRole('heading', { name: 'Recherche globale' })).toHaveCount(0);
  });
});

test.describe("Attestation d'assiduité (EF-REP-006, AC-033)", () => {
  test("l'écran affiche l'émission, la vérification et le registre", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/attestations');
    await expect(page.getByRole('heading', { name: "Attestations d'assiduité" })).toBeVisible();
    // `mat-card-title` n'expose pas le rôle ARIA « heading » : cibler le
    // texte, comme le fait déjà la suite pour les autres cartes Material.
    await expect(page.getByText('Émettre une attestation')).toBeVisible();
    await expect(page.getByText('Vérifier un document')).toBeVisible();
    await expect(page.getByText('Documents émis')).toBeVisible();
  });

  test('un identifiant inconnu est refusé sans divulguer autre chose', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/attestations');
    await page.getByLabel('Identifiant du document').fill('ESIC-ATT-2026-INCONNU00');
    await page.getByRole('button', { name: 'Vérifier' }).click();
    await expect(page.getByText('Aucune attestation ne correspond à cet identifiant.')).toBeVisible({
      timeout: 10_000,
    });
  });

  test("un formateur n'atteint pas l'écran d'attestation", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, '/attestations');
    await expect(page.getByRole('heading', { name: "Attestations d'assiduité" })).toHaveCount(0);
  });
});

test.describe('Abonnement calendrier (EF-INT-001, AC-034)', () => {
  test("un apprenant crée un abonnement, voit le lien une fois, puis le révoque", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/mon-compte/calendrier');
    await expect(page.getByRole('heading', { name: 'Abonnement calendrier' })).toBeVisible();

    // Libellé UNIQUE par exécution : la base de démonstration est
    // persistante, et un libellé fixe ferait révoquer l'abonnement d'un
    // passage précédent au lieu de celui qu'on vient de créer.
    const label = `Recette ${Date.now()}`;
    await page.getByLabel('Nom (facultatif)').fill(label);
    await page.getByRole('button', { name: 'Créer le lien' }).click();

    // Le lien complet n'est affiché qu'une fois : l'écran le dit.
    await expect(page.getByText('il ne sera plus affiché')).toBeVisible({ timeout: 10_000 });
    const url = await page.locator('code.cal__url').innerText();
    expect(url).toContain('/api/v1/calendar/');
    expect(url).toContain('.ics?token=');

    // Le flux répond réellement, hors session applicative : c'est tout
    // l'intérêt d'un abonnement (un agenda ne porte pas de jeton d'accès).
    const feed = await page.request.get(url);
    expect(feed.status()).toBe(200);
    const body = await feed.text();
    expect(body.startsWith('BEGIN:VCALENDAR')).toBe(true);
    expect(body).toContain('PRODID:-//ESIC//ESIC Connect//FR');

    // Révocation de CETTE ligne, retrouvée par son libellé unique.
    const row = page.locator('table.cal__table tbody tr').filter({ hasText: label });
    await expect(row).toHaveCount(1);
    await row.getByRole('button', { name: /Révoquer/ }).click();
    await expect(row.getByRole('cell', { name: 'Révoqué' })).toBeVisible({ timeout: 10_000 });

    // La ligne reste, datée : une révocation n'est pas une suppression
    // (AC-034), et le flux répond « révoqué » plutôt qu'« inconnu ».
    const revoked = await page.request.get(url);
    expect(revoked.status()).toBe(410);
    expect(await revoked.text()).toContain('INT_FEED_REVOKED');
  });

  test("le flux d'un jeton faux répond comme une clé inconnue", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/mon-compte/calendrier');
    const wrong = await page.request.get('/api/v1/calendar/cle-inexistante.ics?token=faux');
    expect(wrong.status()).toBe(404);
  });
});

test.describe("Piste d'audit (EF-AUD-002)", () => {
  /*
   * La CONSULTATION de la piste d'audit n'est pas couverte ici : elle est
   * réservée à `ADMIN` / `SUPER_ADMIN`, et ces comptes n'obtiennent pas de
   * jeton sans second facteur (RG-007). Écrire un test qui la
   * contournerait donnerait une fausse preuve. Elle est couverte côté
   * serveur par `AuditQueryIntegrationTests` et côté écran par
   * `audit-trail.spec.ts` ; la limite est notée dans
   * `docs/STATUS.md` §6.
   *
   * Ce qui est vérifiable sans second facteur — et qui compte autant —
   * c'est le refus opposé aux autres rôles.
   */
  test("un responsable pédagogique n'atteint pas la piste d'audit", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/exploitation/audit');
    await expect(page.getByRole('heading', { name: "Piste d'audit" })).toHaveCount(0);
  });

  test("un formateur n'atteint pas la piste d'audit", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, '/exploitation/audit');
    await expect(page.getByRole('heading', { name: "Piste d'audit" })).toHaveCount(0);
  });
});

test.describe('Invitations non activées (EF-REP-010)', () => {
  test("l'écran s'affiche et ne montre que des adresses masquées", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/invitations/non-activees');
    // ANO-NAV-001 : « Non activées » est devenu une vue de « Invitations »,
    // reliée par une sous-navigation `.esic-subnav`. Le titre de page est
    // désormais « Invitations », l'onglet actif « Non activées ».
    await expect(page.getByRole('heading', { name: 'Invitations', exact: true })).toBeVisible();
    await expect(page.locator('.esic-subnav__link--active')).toHaveText('Non activées');
    await expect(page.getByText('corrigez-la depuis le suivi des invitations')).toBeVisible();
    const table = page.locator('table.pending__table');
    const empty = page.getByText("Aucun compte en attente d'activation.");
    await expect(table.or(empty)).toBeVisible({ timeout: 10_000 });
    if (await table.isVisible()) {
      // Une adresse complète du jeu de démonstration ne doit jamais
      // apparaître : seule sa forme masquée est servie.
      expect(await table.innerText()).not.toContain('@example.test');
    }
  });
});

test.describe('Tableau équivalent des graphiques (EF-REP-008)', () => {
  test("le tableau de bord du responsable double son graphique d'une table accessible", async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER);
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
    await expect(page.getByText("Taux d'assiduité par classe")).toBeVisible({ timeout: 10_000 });

    const chart = page.locator('.dashboard__chart');
    if (await chart.count()) {
      // Chaque barre porte sa valeur en toutes lettres : la couleur n'est
      // jamais seule porteuse de l'information (docs/02 §32.5).
      await expect(page.locator('.dashboard__bar-value').first()).not.toBeEmpty();
      // Et un tableau équivalent, avec sa légende, accompagne le graphique.
      await expect(page.locator('table.dashboard__table caption').first()).toContainText(
        'Tableau équivalent',
      );
    }
  });
});
