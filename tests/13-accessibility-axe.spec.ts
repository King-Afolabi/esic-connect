import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdirSync } from 'node:fs';

/**
 * Audit d'accessibilité automatisé (Lot M) et captures d'écran (Lot N)
 * des écrans PUBLICS — connexion, mot de passe oublié — qui n'exigent
 * aucune authentification.
 *
 * Les écrans authentifiés relèvent de la pile de démonstration (profil
 * Spring `demo`, base `esic_connect_demo`, second facteur déterministe) :
 * voir `docs/audit/FINAL-ONE-SHOT-REPORT.md` pour l'état de cette
 * exécution.
 *
 * Règle : aucune violation `critical` ni `serious` non justifiée. Les
 * limites d'un audit automatisé (il ne couvre pas tout) sont
 * documentées dans le rapport.
 */

const SHOTS = 'artifacts/report-screenshots';
mkdirSync(SHOTS, { recursive: true });

const PUBLIC_ROUTES = [
  { path: '/login', name: 'login' },
  { path: '/login?reason=expired', name: 'login-expired' },
  { path: '/mot-de-passe-oublie', name: 'forgot-password' },
];

const VIEWPORTS = [
  { w: 1440, h: 900, tag: 'desktop-1440x900' },
  { w: 390, h: 844, tag: 'mobile-portrait-390x844' },
  { w: 844, h: 390, tag: 'mobile-landscape-844x390' },
  { w: 768, h: 1024, tag: 'tablet-portrait-768x1024' },
  { w: 1024, h: 768, tag: 'tablet-landscape-1024x768' },
];

test.describe('Accessibilité — écrans publics (Lot M)', () => {
  for (const route of PUBLIC_ROUTES) {
    test(`axe : aucune violation critique/sérieuse sur ${route.path}`, async ({ page }) => {
      await page.goto(route.path);
      await expect(page.locator('main')).toBeVisible();

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();

      const blocking = results.violations.filter(
        (v) => v.impact === 'critical' || v.impact === 'serious',
      );
      if (blocking.length > 0) {
        console.error(
          JSON.stringify(
            blocking.map((v) => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.length })),
            null,
            2,
          ),
        );
      }
      expect(blocking, `${blocking.length} violation(s) bloquante(s) sur ${route.path}`).toEqual([]);
    });
  }

  test('connexion : navigable entièrement au clavier, focus visible', async ({ page }) => {
    await page.goto('/login');
    // Le lien d'évitement est la première cible tabulable.
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: /aller au contenu|contenu principal/i })).toBeFocused();
    // On atteint les champs et le bouton de soumission au clavier.
    await page.getByLabel('Adresse électronique').focus();
    await page.keyboard.type('demo@example.test');
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Mot de passe')).toBeFocused();
    await page.keyboard.type('secret');
    await page.keyboard.press('Tab');
    // Bouton « Se connecter » (exact — un second bouton clé d'accès existe).
    const submit = page.getByRole('button', { name: 'Se connecter', exact: true });
    await expect(submit).toBeVisible();
  });

  test('connexion : aucun débordement horizontal au zoom 200 % (mobile portrait)', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/login');
    await page.evaluate(() => {
      document.documentElement.style.zoom = '2';
    });
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    );
    expect(overflow, 'la page défile horizontalement au zoom 200 %').toBe(false);
  });
});

test.describe('Captures des écrans publics (Lot N)', () => {
  for (const route of PUBLIC_ROUTES) {
    for (const vp of VIEWPORTS) {
      test(`capture ${route.name} — ${vp.tag}`, async ({ page }) => {
        await page.setViewportSize({ width: vp.w, height: vp.h });
        await page.goto(route.path);
        await expect(page.locator('main')).toBeVisible();
        await page.waitForTimeout(150);
        await page.screenshot({
          path: `${SHOTS}/pub-${route.name}-${vp.tag}.png`,
          fullPage: false,
        });
      });
    }
  }
});
