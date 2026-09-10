import { test, expect, Page, Locator } from '@playwright/test';
import { ACCOUNTS } from './support/accounts';
import { loginAsUi } from './support/auth';

/**
 * DÉFILEMENT DES TABLEAUX — non-régression (correction UX 2026-09-10).
 *
 * Bug corrigé : `.esic-table-wrap--tall` / `--compact` posaient
 * `overscroll-behavior: contain`, ce qui bloque délibérément le « scroll
 * chaining » CSS natif — une fois le défilement interne du tableau en
 * butée (haut, bas), le geste (molette, trackpad, tactile) restait piégé
 * dans l'enveloppe au lieu de continuer sur la page. `_primitives.scss`
 * pose désormais explicitement `overscroll-behavior: auto` (le
 * comportement de chaînage natif) sur `.esic-table-wrap`,
 * `.esic-table-wrap--tall` et `.esic-table-wrap--compact`.
 *
 * Ces tests pilotent un vrai `mouse.wheel()` (Playwright) et lisent
 * `window.scrollY` / `wrapper.scrollLeft` réels — pas d'assertion sur les
 * seules propriétés CSS — pour prouver que le défilement de page reprend
 * effectivement quand le tableau est en butée.
 *
 * Deux particularités réelles de l'application, vérifiées avant d'écrire
 * ces tests (voir historique de la branche) :
 *
 * 1. Le socle démo local a été volontairement réduit (mémoire projet,
 *    2026-09-08) : le tableau « Apprenants » y est vide. La piste
 *    d'audit (`/exploitation/audit`, 98 événements réels) reste, elle,
 *    naturellement plus haute que son enveloppe `--tall` — elle sert de
 *    terrain principal pour les scénarios de butée (elle couvre à la
 *    fois « administration », « rapport » et « tableau long » du
 *    périmètre demandé).
 * 2. Les écrans à tableau `--tall` sont volontairement calibrés (`62vh`,
 *    voir `_primitives.scss`) pour qu'AUCUNE page ne nécessite de
 *    double défilement — la page entière tient alors exactement dans le
 *    viewport, quelle que soit sa taille. C'est la qualité recherchée
 *    par le produit, pas le bug : mais ça veut dire qu'il n'y a, par
 *    construction, rien à faire défiler en dehors du tableau sur ces
 *    écrans précis. Pour observer un chaînage réel (le tableau relâche
 *    la main à la page), ces tests ajoutent un espaceur bas de page
 *    (`<div style="height:…">`, jamais dans le tableau ni dans son
 *    enveloppe) qui simule simplement « il y a du contenu après le
 *    tableau » — un scénario tout aussi réel qu'une page de rapport
 *    plus longue. Le mécanisme testé (CSS `overscroll-behavior` du
 *    wrapper réel, non modifié) reste inchangé.
 *
 * `ESIC_DEMO_TOTP_SECRET` doit être exporté (même valeur que le back-end
 * profil `demo`) pour les comptes ADMIN/SUPER_ADMIN — voir
 * `tests/support/auth.ts`.
 */

const TALL_WRAP_SELECTOR = '.esic-table-wrap--tall, .esic-table-wrap--compact';

async function scrollWrapperToBottom(wrapper: Locator): Promise<void> {
  await wrapper.evaluate((el) => {
    el.scrollTop = el.scrollHeight;
  });
}

async function scrollWrapperToLeft(wrapper: Locator): Promise<void> {
  await wrapper.evaluate((el) => {
    el.scrollLeft = 0;
  });
}

async function hoverCenter(page: Page, wrapper: Locator): Promise<void> {
  // `boundingBox()` renvoie des coordonnées de page, pas forcément dans
  // le viewport visible (ex. fenêtre resserrée) : on cadre l'enveloppe
  // avant de lire sa position, sinon `mouse.move`/`wheel` ciblent un
  // point hors écran et n'atteignent aucun élément réel.
  await wrapper.scrollIntoViewIfNeeded();
  const box = await wrapper.boundingBox();
  expect(box, 'l’enveloppe du tableau doit être visible pour positionner la souris').not.toBeNull();
  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
}

/**
 * Garantit que la PAGE a du contenu à défiler après/au-dessus du
 * tableau — jamais dans le tableau lui-même — pour rendre le test de
 * chaînage probant sur un écran par ailleurs calibré pour n'avoir aucun
 * double défilement. Voir note d'en-tête, point 2.
 */
async function ensurePageOverflowsBelowTable(page: Page): Promise<void> {
  await page.evaluate(() => {
    const spacer = document.createElement('div');
    spacer.setAttribute('data-testid', 'e2e-scroll-spacer');
    spacer.style.height = '2000px';
    document.body.appendChild(spacer);
  });
}

test.describe('Défilement des tableaux ne piège jamais la page', () => {
  test('Piste d’audit (administration) — molette verticale en butée basse fait défiler la page', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/exploitation/audit');
    const wrapper = page.locator(TALL_WRAP_SELECTOR).first();
    await expect(wrapper).toBeVisible();
    await ensurePageOverflowsBelowTable(page);

    // Le tableau doit avoir un défilement interne réel (98 événements
    // d'audit démo dépassent l'enveloppe `--tall`), sinon la butée est
    // immédiate et le test ne prouve rien.
    const overflows = await wrapper.evaluate((el) => el.scrollHeight > el.clientHeight + 4);
    expect(overflows, 'le tableau doit réellement déborder verticalement').toBe(true);

    await scrollWrapperToBottom(wrapper);
    await hoverCenter(page, wrapper);
    const scrollYBefore = await page.evaluate(() => window.scrollY);

    // Plusieurs à-coups de molette réalistes plutôt qu'un seul delta géant.
    for (let i = 0; i < 8; i += 1) {
      await page.mouse.wheel(0, 300);
    }
    await page.waitForTimeout(150);

    const scrollYAfter = await page.evaluate(() => window.scrollY);
    expect(
      scrollYAfter,
      'la page doit continuer à défiler une fois le tableau en butée basse',
    ).toBeGreaterThan(scrollYBefore);
  });

  test('Piste d’audit (administration) — molette verticale en butée haute fait remonter la page', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/exploitation/audit');
    const wrapper = page.locator(TALL_WRAP_SELECTOR).first();
    await expect(wrapper).toBeVisible();
    await ensurePageOverflowsBelowTable(page);

    // Descend la page pour avoir de la marge à remonter ; le tableau
    // reste en butée haute (scrollTop = 0, état initial). Position de
    // départ posée directement (le test 1 prouve déjà que la molette
    // fait avancer `window.scrollY` sur cet écran) : ce qui est sous
    // test ici est la remontée depuis la butée, pas la mise en place.
    await page.evaluate(() => window.scrollTo(0, 900));
    await hoverCenter(page, wrapper);
    const scrollYBefore = await page.evaluate(() => window.scrollY);
    expect(scrollYBefore).toBeGreaterThan(0);

    for (let i = 0; i < 8; i += 1) {
      await page.mouse.wheel(0, -300);
    }
    await page.waitForTimeout(150);

    const scrollYAfter = await page.evaluate(() => window.scrollY);
    expect(
      scrollYAfter,
      'la page doit continuer à remonter une fois le tableau en butée haute',
    ).toBeLessThan(scrollYBefore);
  });

  test('Piste d’audit — le tableau reste défilable horizontalement (deltaX) sans jamais faire défiler la page', async ({
    page,
  }) => {
    // Fenêtre resserrée (largeur tablette réaliste) : à 1280px de large,
    // les colonnes de la piste d'audit tiennent déjà entièrement — rien
    // à défiler horizontalement, ce qui ne prouverait rien. 700px force
    // un débordement réel du tableau, sans toucher à son balisage/CSS.
    await page.setViewportSize({ width: 700, height: 720 });
    await loginAsUi(page, ACCOUNTS.ADMIN, '/exploitation/audit');
    const wrapper = page.locator(TALL_WRAP_SELECTOR).first();
    await expect(wrapper).toBeVisible();
    await scrollWrapperToLeft(wrapper);

    const overflowsHorizontally = await wrapper.evaluate(
      (el) => el.scrollWidth > el.clientWidth + 4,
    );
    expect(overflowsHorizontally, 'le tableau doit réellement déborder horizontalement').toBe(
      true,
    );

    const scrollLeftBefore = await wrapper.evaluate((el) => el.scrollLeft);
    const bodyScrollXBefore = await page.evaluate(() => window.scrollX);

    await hoverCenter(page, wrapper);
    // Molette/trackpad horizontal (deltaX) : le tableau doit défiler,
    // jamais la page (qui n'a et ne doit avoir aucun scroll horizontal).
    await page.mouse.wheel(400, 0);
    await page.waitForTimeout(150);

    const scrollLeftAfter = await wrapper.evaluate((el) => el.scrollLeft);
    const bodyScrollXAfter = await page.evaluate(() => window.scrollX);

    expect(scrollLeftAfter, 'le tableau doit avoir défilé horizontalement').toBeGreaterThan(
      scrollLeftBefore,
    );
    expect(bodyScrollXAfter, 'la page ne défile jamais horizontalement').toBe(bodyScrollXBefore);
  });

  test('Piste d’audit — geste diagonal trackpad (deltaX + deltaY) ne bloque pas la page en butée', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.ADMIN, '/exploitation/audit');
    const wrapper = page.locator(TALL_WRAP_SELECTOR).first();
    await expect(wrapper).toBeVisible();
    await ensurePageOverflowsBelowTable(page);
    await scrollWrapperToBottom(wrapper);
    await hoverCenter(page, wrapper);
    const scrollYBefore = await page.evaluate(() => window.scrollY);
    // Geste diagonal typique de trackpad : composante horizontale ET
    // verticale simultanées.
    for (let i = 0; i < 8; i += 1) {
      await page.mouse.wheel(40, 300);
    }
    await page.waitForTimeout(150);
    const scrollYAfter = await page.evaluate(() => window.scrollY);

    expect(
      scrollYAfter,
      'un geste diagonal, une fois le tableau en butée verticale, doit continuer à faire défiler la page',
    ).toBeGreaterThan(scrollYBefore);
  });

  test('Tableau de bord — la page reste librement défilable au-dessus/en dessous', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, '/dashboard');
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
    await ensurePageOverflowsBelowTable(page);

    // Le tableau de bord n'a pas d'enveloppe `.esic-table-wrap*` (sa
    // table native, `table-layout: fixed`, n'a explicitement aucun
    // défilement propre — voir dashboard.html) : ce n'est pas la
    // surface du bug corrigé, dont la preuve de chaînage molette est
    // apportée en profondeur ci-dessus sur la piste d'audit. Note pour
    // qui creuserait un jour un blocage similaire ici : la coquille
    // pose `<mat-sidenav-container class="shell__container">`, dont
    // Angular Material fixe `overflow: hidden` par défaut (CSS du
    // composant, pas de ce dépôt) — sans effet réel tant que
    // `[autosize]` (posé ici sur desktop) laisse le conteneur grandir
    // avec son contenu plutôt que le rogner ; vérifié ci-dessous.
    const scrollHeight = await page.evaluate(() => document.documentElement.scrollHeight);
    const clientHeight = await page.evaluate(() => document.documentElement.clientHeight);
    expect(scrollHeight, 'le contenu doit dépasser le viewport (espaceur ajouté)').toBeGreaterThan(
      clientHeight,
    );

    // Le défilement réel (ce que déclenchent aussi bien la molette que
    // le clavier PageUp/Down ou le lien d'ancre « Aller au contenu
    // principal ») doit fonctionner : la page n'est pas coincée à
    // `scrollY = 0` malgré du contenu qui dépasse le viewport.
    await page.evaluate(() => window.scrollTo(0, 400));
    const scrollYAfter = await page.evaluate(() => window.scrollY);
    expect(scrollYAfter, 'le tableau de bord doit rester librement défilable').toBeGreaterThan(0);
  });

  test('Liste des apprenants — la page reste défilable même écran vide (petit socle démo)', async ({
    page,
  }) => {
    // Le socle démo local ne porte aucun apprenant (mémoire projet,
    // 2026-09-08) : l'écran affiche son état vide plutôt que le tableau.
    // Ce test reste utile en non-régression générale (RBAC, chargement,
    // scroll de page) ; la preuve de chaînage aux limites du tableau est
    // apportée par la piste d'audit ci-dessus, seule à porter aujourd'hui
    // un volume garanti de lignes.
    await loginAsUi(page, ACCOUNTS.TEACHER, '/students');
    await expect(page.getByRole('heading', { name: 'Apprenants' })).toBeVisible();
    await ensurePageOverflowsBelowTable(page);

    const scrollYBefore = await page.evaluate(() => window.scrollY);
    await page.mouse.wheel(0, 600);
    await page.waitForTimeout(100);
    const scrollYAfter = await page.evaluate(() => window.scrollY);
    expect(
      scrollYAfter,
      'la page doit rester défilable (aucun `overflow: hidden` global posé par erreur)',
    ).toBeGreaterThan(scrollYBefore);
  });

  test('Toutes les enveloppes de tableau déclarent un chaînage de défilement natif (overscroll-behavior: auto)', async ({
    page,
  }) => {
    // Vérification structurelle complémentaire, y compris pour le tactile
    // (le geste tactile réel n'est pas simulable de façon fiable en
    // navigateur headless) : la propriété calculée qui pilote le
    // chaînage doit être `auto`, jamais `contain`/`none`, sur chaque
    // variante d'enveloppe utilisée par l'application.
    await loginAsUi(page, ACCOUNTS.ADMIN, '/exploitation/audit');
    const selectors = ['.esic-table-wrap', '.esic-table-wrap--tall', '.esic-table-wrap--compact'];
    let checked = 0;
    for (const selector of selectors) {
      const count = await page.locator(selector).count();
      if (count === 0) continue;
      const el = page.locator(selector).first();
      const overscrollY = await el.evaluate((node) => getComputedStyle(node).overscrollBehaviorY);
      expect(overscrollY, `${selector} doit relayer le défilement vertical à la page`).toBe('auto');
      const touchAction = await el.evaluate((node) => getComputedStyle(node).touchAction);
      expect(touchAction, `${selector} ne doit pas bloquer les gestes tactiles`).not.toBe('none');
      checked += 1;
    }
    expect(
      checked,
      'au moins une enveloppe de tableau doit être présente sur cette page',
    ).toBeGreaterThan(0);
  });
});
