import axe, { type RunOptions, type Result } from 'axe-core';

/**
 * Vérification d'accessibilité automatisée minimale (FINAL-020).
 *
 * `axe-core` s'exécute ici dans **jsdom** (pas de vrai moteur de rendu) :
 * les règles qui dépendent de la mise en page réelle ne peuvent pas être
 * évaluées de façon fiable et sont désactivées (`color-contrast`, règles
 * de repères de page). On teste des **composants isolés**, pas une page
 * complète : les règles « la page a un seul `main` / un `h1` » sont donc
 * aussi désactivées.
 *
 * Ce que ce garde-fou attrape utilement : libellés de formulaire
 * manquants ou mal associés, `aria-*` invalides, boutons / liens sans nom
 * accessible, `role` incohérent, `img` sans alternative, listes mal
 * structurées, attributs dupliqués.
 *
 * Ce n'est PAS un audit d'accessibilité complet : le contraste, le zoom,
 * le parcours lecteur d'écran réel et les tests manuels restent
 * nécessaires (voir `docs/08-tests-recette.md` §16).
 */
const JSDOM_SAFE_OPTIONS: RunOptions = {
  runOnly: ['wcag2a', 'wcag2aa', 'best-practice'],
  rules: {
    // Nécessite un rendu réel (couleurs calculées) — impossible en jsdom.
    'color-contrast': { enabled: false },
    // Règles « page entière » : hors sujet pour un composant isolé.
    region: { enabled: false },
    'landmark-one-main': { enabled: false },
    'page-has-heading-one': { enabled: false },
    'landmark-unique': { enabled: false },
  },
};

function formatViolations(violations: Result[]): string {
  return violations
    .map((v) => {
      const nodes = v.nodes.map((n) => `      ${n.target.join(' ')} — ${n.failureSummary ?? ''}`).join('\n');
      return `  [${v.impact ?? 'n/a'}] ${v.id}: ${v.help}\n${nodes}`;
    })
    .join('\n');
}

/**
 * Lance axe-core sur `element` et échoue (via `expect`) si une violation
 * est détectée. Utiliser après `fixture.detectChanges()`.
 */
export async function expectNoAxeViolations(
  element: Element,
  overrides: RunOptions = {},
): Promise<void> {
  const results = await axe.run(element, { ...JSDOM_SAFE_OPTIONS, ...overrides });
  if (results.violations.length > 0) {
    throw new Error(
      `axe-core a détecté ${results.violations.length} violation(s) d'accessibilité :\n` +
        formatViolations(results.violations),
    );
  }
}
