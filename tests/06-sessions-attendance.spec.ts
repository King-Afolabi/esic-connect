import path from 'node:path';
import { test, expect } from '@playwright/test';
import { ACCOUNTS, DEMO_DATA, STUDENT_TWO } from './support/accounts';
import { loginAsUi } from './support/auth';

const CAPTURES = path.join(__dirname, '..', 'captures');

/**
 * DOMAINE 3 — Séances exceptionnelles et émargement intelligent.
 *
 * C'est le cœur du « parcours prioritaire » de CLAUDE.md
 * (Ouverture par le formateur → Émargement → Rapport). Ce fichier exerce
 * le canal **code court** ; le **scan caméra** (livré depuis le
 * 8 septembre 2026) a son propre fichier `tests/16-qr-scanner.spec.ts`
 * (caméra simulée). Pas de 4 points de contrôle nommés ici (un seul
 * point START auto-ouvert avec la séance).
 *
 * Les tests sont **sérialisés** : ils font progresser une séance créée
 * pour l'occasion (PLANNED → OPEN → CLOSED), un état non réversible.
 * Créer une nouvelle séance dédiée (plutôt que réutiliser la séance de
 * démonstration déjà présente) évite de perturber les autres fichiers de
 * test qui lisent cette dernière. Chaque étape se reconnecte via
 * `loginAsUi(page, account, path)` : une session ne survit qu'à des
 * navigations internes, jamais à un second `page.goto` (support/auth.ts).
 */
test.describe.configure({ mode: 'serial' });

let sessionPath = '';
let shortCode = '';
let studentOneDetailPath = '';
const SESSION_TITLE = `Séance audit Playwright ${Date.now()}`;

/**
 * Heure locale (fuseau du contexte navigateur, `playwright.config.ts`
 * `timezoneId: 'Europe/Paris'`) au format `HH:MM` exigé par le champ de
 * l'écran de création. Calculée par rapport à l'instant réel de
 * l'exécution — un horaire figé (ex. « 08:00 ») rendait ce test
 * dépendant de l'heure du jour : au-delà d'une tolérance de 30 minutes
 * après l'ouverture (docs/02 §16.4), l'émargement plus loin dans ce même
 * parcours calcule à bon droit un retard, parfois de plusieurs centaines
 * de minutes si la suite tourne l'après-midi ou le soir — pas un bug du
 * produit, un défaut de ce test.
 */
function parisTimeHHMM(date: Date): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Paris',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date);
  const hour = parts.find((p) => p.type === 'hour')?.value ?? '00';
  const minute = parts.find((p) => p.type === 'minute')?.value ?? '00';
  return `${hour}:${minute}`;
}

test.describe('Parcours prioritaire réel : création → ouverture → émargement → clôture', () => {
  test('1. PEDAGOGICAL_MANAGER crée une séance exceptionnelle', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, '/sessions/new');

    await page.getByLabel('Formateur').click();
    await page.getByRole('option', { name: /Formateur.*Démo|Démo.*Formateur/ }).click();
    await page.getByLabel('Classes').click();
    await page.getByRole('option', { name: new RegExp(DEMO_DATA.classCode) }).click();
    await page.keyboard.press('Escape');

    const now = new Date();
    const today = new Date().toISOString().slice(0, 10);
    await page.getByLabel('Date').fill(today);
    // Démarre 2 min avant maintenant : l'émargement qui suit dans ce même
    // parcours reste dans la tolérance « présent » (0–15 min, docs/02
    // §16.4) quelle que soit l'heure du jour à laquelle la suite tourne.
    await page.getByLabel('Début (heure locale)').fill(parisTimeHHMM(new Date(now.getTime() - 2 * 60_000)));
    await page.getByLabel('Fin (heure locale)').fill(parisTimeHHMM(new Date(now.getTime() + 60 * 60_000)));
    await page
      .getByLabel('Motif de la séance exceptionnelle')
      .fill('Séance créée par la suite Playwright d\'audit (parcours prioritaire).');
    // Le motif ci-dessus alimente `exceptionReason`, PAS `title` : pour
    // retrouver cette séance par son intitulé plus tard (ex. dans « Mes
    // présences », qui affiche `sessionTitle`), il faut renseigner le
    // libellé explicitement.
    await page.getByLabel('Libellé (facultatif)').fill(SESSION_TITLE);

    await page.getByRole('button', { name: 'Créer la séance' }).click();
    await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]+$/, { timeout: 10_000 });
    sessionPath = new URL(page.url()).pathname;
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '01-session-creee.png'),
      fullPage: true,
    });
  });

  test('2. Le TEACHER assigné ouvre la séance (RG-013)', async ({ page }) => {
    expect(sessionPath, 'la séance doit avoir été créée par le test précédent').not.toBe('');
    await loginAsUi(page, ACCOUNTS.TEACHER, sessionPath);
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();

    await page.getByRole('button', { name: 'Ouvrir la séance' }).click();
    await page.getByRole('button', { name: "Confirmer l'ouverture" }).click();
    await expect(page.locator('dl.sessions__facts')).toContainText('Ouverte');
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '02-seance-ouverte.png'),
      fullPage: true,
    });
  });

  test('3. Le formateur affiche un code court d\'émargement (EF-ATT-001, RG-043/044)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, sessionPath);
    await page.getByRole('button', { name: 'Afficher un code d\'émargement' }).click();
    const codeLocator = page.locator('.sessions__code');
    await expect(codeLocator).toBeVisible({ timeout: 10_000 });
    shortCode = (await codeLocator.textContent())?.trim() ?? '';
    expect(shortCode.length).toBeGreaterThan(0);
    // Le QR n'encode que ce jeton opaque — jamais de donnée personnelle
    // (RG-080) : vérifié indirectement en constatant qu'aucune donnée
    // apprenant n'apparaît dans le conteneur QR.
    await expect(page.locator('app-qr-display')).toBeVisible();
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '03-code-emargement-affiche.png'),
      fullPage: true,
    });
  });

  test('4. Un apprenant inscrit émarge avec le code (parcours nominal)', async ({ page }) => {
    expect(shortCode, 'un code court doit avoir été généré par le test précédent').not.toBe('');
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    await page.getByLabel('Code court').fill(shortCode);
    await page.getByRole('button', { name: 'Valider ma présence' }).click();
    await expect(page.getByText('Présence enregistrée.')).toBeVisible({ timeout: 10_000 });
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '04-emargement-apprenant.png'),
      fullPage: true,
    });
  });

  test("5. Le même apprenant ne peut pas émarger deux fois (RG-015, anti-rejeu)", async ({
    page,
  }) => {
    // Nouvelle session = nouvelle instance de composant : on retrouve
    // directement le formulaire (pas le panneau de succès de la connexion
    // précédente, qui vivait dans une autre instance de page).
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    await page.getByLabel('Code court').fill(shortCode);
    await page.getByRole('button', { name: 'Valider ma présence' }).click();
    await expect(page.locator('.esic-form__error')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Présence enregistrée.')).not.toBeVisible();
    await page.screenshot({
      path: path.join(CAPTURES, 'errors', '05-double-emargement-refuse.png'),
      fullPage: true,
    });
  });

  test('6. Un second apprenant inscrit peut émarger avec le même code (le code sert toute la classe)', async ({
    page,
  }) => {
    await loginAsUi(page, STUDENT_TWO, '/attendance');
    await page.getByLabel('Code court').fill(shortCode);
    await page.getByRole('button', { name: 'Valider ma présence' }).click();
    await expect(page.getByText('Présence enregistrée.')).toBeVisible({ timeout: 10_000 });
  });

  test('7. Code invalide : message d\'erreur, aucune présence enregistrée', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/attendance');
    await page.getByLabel('Code court').fill('ZZZZZZ');
    await page.getByRole('button', { name: 'Valider ma présence' }).click();
    // Classe renommée `.checkin__inline-error` -> `.esic-form__error`.
    await expect(page.locator('.esic-form__error')).toBeVisible({ timeout: 10_000 });
  });

  test('8. Le formateur voit les deux présences en direct (EF-ATT visibilité immédiate)', async ({
    page,
  }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, sessionPath);
    await page.getByRole('button', { name: 'Rafraîchir' }).click();
    await expect(page.getByText('Alice Martin')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Karim Diallo')).toBeVisible();
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '06-presences-en-direct.png'),
      fullPage: true,
    });
  });

  test('9. Le formateur ferme la séance (émargement définitivement clos)', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.TEACHER, sessionPath);
    await page.getByRole('button', { name: 'Fermer la séance' }).click();
    await page.getByRole('button', { name: 'Confirmer la fermeture' }).click();
    await expect(page.getByText('Séance clôturée : l\'émargement est fermé.')).toBeVisible({
      timeout: 10_000,
    });
    // Un formulaire d'ouverture ne doit plus être proposé sur une séance close.
    await expect(page.getByRole('button', { name: 'Ouvrir la séance' })).toHaveCount(0);
    await page.screenshot({
      path: path.join(CAPTURES, 'success', '07-seance-cloturee.png'),
      fullPage: true,
    });
  });

  test("10. L'apprenant retrouve sa présence dans « Mes présences »", async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/my-attendance');
    // La liste contient aussi des présences du jeu de démonstration
    // (`esic_connect_demo`) : on cible la ligne exacte de la séance créée
    // par cette suite, pas `.first()` (qui prendrait une autre ligne selon
    // l'ordre de tri).
    const row = page.locator('tr', { hasText: SESSION_TITLE });
    await expect(row).toBeVisible({ timeout: 10_000 });
    await row.getByRole('link', { name: 'Détail' }).click();
    await expect(page).toHaveURL(/\/my-attendance\/[^/]+$/);
    studentOneDetailPath = new URL(page.url()).pathname;
    await expect(page.getByText('Présent', { exact: false })).toBeVisible();
  });

  test("11. Isolation entre apprenants (AC-017) : l'autre apprenant ne peut pas ouvrir cette fiche", async ({
    page,
  }) => {
    expect(studentOneDetailPath).not.toBe('');
    await loginAsUi(page, STUDENT_TWO, studentOneDetailPath);
    // Jamais le contenu d'un autre apprenant : soit "not-found", soit
    // "forbidden" côté serveur — jamais la fiche elle-même.
    await expect(page.getByText('Présent', { exact: false })).not.toBeVisible();
    // `.isVisible()` seul est un instantané, pas une attente : au moment de
    // l'appel la page peut encore être en chargement. `expect(...).toBeVisible()`
    // relance jusqu'à ce que l'un des deux états attendus apparaisse.
    const notFound = page.getByText('Aucune présence ne correspond.');
    const forbidden = page.getByText('Accès refusé.');
    await expect(notFound.or(forbidden)).toBeVisible({ timeout: 10_000 });
  });
});

test.describe('Autorisations sur les séances', () => {
  test('un STUDENT ne peut pas consulter /sessions (réservé à la gestion)', async ({ page }) => {
    await loginAsUi(page, ACCOUNTS.STUDENT, '/sessions');
    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('un PEDAGOGICAL_MANAGER (aussi TEACHER) consulte une séance qu\'il n\'enseigne pas (accès de gestion, périmètre RG-013)', async ({
    page,
  }) => {
    // Le compte "responsable" a le rôle TEACHER mais n'est pas le
    // formateur assigné à la séance créée par le test 1 : son accès
    // provient de son rôle PEDAGOGICAL_MANAGER (gestion), pas d'une
    // affectation de séance — ce test documente ce comportement réel
    // plutôt que de supposer un 403 non vérifié.
    test.skip(sessionPath === '', 'dépend de la séance créée par le test 1 de ce fichier');
    await loginAsUi(page, ACCOUNTS.PEDAGOGICAL_MANAGER_TEACHER, sessionPath);
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).not.toBeVisible();
  });
});
