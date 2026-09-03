import { defineConfig, devices } from '@playwright/test';

/**
 * Suite d'audit E2E — ESIC Connect.
 *
 * Portée : cette suite pilote un navigateur réel contre l'application
 * réellement déployée localement (`ng serve` sur :4200, backend Spring Boot
 * sur :8080, profil `demo` / base `esic_connect_demo`). Elle ne couvre QUE
 * les écrans qui existent réellement dans `frontend/src/app/app.routes.ts` ;
 * voir `audit-report.md` pour la matrice complète fonctionnalité attendue →
 * implémentée → testée, y compris les domaines volontairement hors
 * périmètre (aucun test n'est écrit contre un écran qui n'existe pas).
 *
 * Le projet a documenté un choix explicite de NE PAS construire de suite
 * e2e navigateur (`docs/CURRENT-STATE.md`, décision `DEC-G1-011`) au profit
 * de tests d'intégration API. Cette suite est donc un complément demandé
 * explicitement pour cet audit, pas un remplacement de cette décision.
 */
export default defineConfig({
  testDir: './tests',
  testMatch: ['**/*.spec.ts'],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // Beaucoup de scénarios mutent un état partagé (comptes démo, séance
  // créée pour le parcours prioritaire) : un seul worker évite les courses
  // entre fichiers de tests indépendants.
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 8_000 },
  reporter: [
    ['html', { open: 'never', outputFolder: 'test-results/html-report' }],
    ['list'],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  outputDir: 'test-results/artifacts',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    locale: 'fr-FR',
    timezoneId: 'Europe/Paris',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Disponibles à la demande (`npx playwright test --project=firefox`) :
    // non exécutés par défaut pour garder la campagne d'audit dans un
    // délai raisonnable.
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 7'] },
    },
  ],
});
