/**
 * Comptes de démonstration réels, amorcés par `DemoDataInitializer`
 * (backend/src/main/java/com/esic/connect/bootstrap/DemoDataInitializer.java)
 * sous le profil Spring `demo` (base `esic_connect_demo`).
 *
 * Le mot de passe est identique pour les 6 comptes (ESIC_DEMO_PASSWORD,
 * défini localement dans `.env`, jamais commité). Il est lu ici depuis une
 * variable d'environnement plutôt que codé en dur, pour ne jamais faire
 * porter un secret par le dépôt (règle du dépôt : « ne jamais enregistrer
 * de secret dans Git »).
 */
function requiredDemoPassword(): string {
  const value = process.env.ESIC_DEMO_PASSWORD;
  if (!value) {
    throw new Error(
      'ESIC_DEMO_PASSWORD est absent de l\'environnement. ' +
        'Exportez-le avant de lancer la suite : `set -a && source .env && set +a && npm run test:e2e`. ' +
        'Aucune valeur de repli n\'est fournie : le dépôt ne doit porter aucun mot de passe.',
    );
  }
  return value;
}

export const DEMO_PASSWORD = requiredDemoPassword();

/**
 * Secret TOTP déterministe (ESIC_DEMO_TOTP_SECRET) des comptes fictifs
 * ADMIN / SUPER_ADMIN, pour lesquels le second facteur est OBLIGATOIRE
 * (RG-007) — voir `DemoDataInitializer` (backend, profil `demo`) et
 * `docs/STATUS.md` (limites connues).
 *
 * Volontairement **non lu de façon stricte** ici (contrairement à
 * `requiredDemoPassword`) : de nombreux tests n'utilisent ni ADMIN ni
 * SUPER_ADMIN et ne doivent pas échouer faute de cette variable. L'erreur
 * explicite n'est levée qu'au moment réel de l'usage, dans
 * `loginAsUi` (`tests/support/auth.ts`).
 */
export function demoTotpSecret(): string | undefined {
  const value = process.env.ESIC_DEMO_TOTP_SECRET;
  return value && value.trim().length > 0 ? value.trim() : undefined;
}

export type DemoRole =
  | 'SUPER_ADMIN'
  | 'ADMIN'
  | 'TEACHER'
  | 'STUDENT'
  | 'PEDAGOGICAL_MANAGER_TEACHER';

export interface DemoAccount {
  role: DemoRole;
  email: string;
  password: string;
  roles: string[];
  label: string;
  /** Renseigné uniquement pour les rôles où le second facteur est obligatoire. */
  totpSecret?: string;
}

export const ACCOUNTS: Record<DemoRole, DemoAccount> = {
  SUPER_ADMIN: {
    role: 'SUPER_ADMIN',
    email: 'superadmin@example.test',
    password: DEMO_PASSWORD,
    roles: ['SUPER_ADMIN'],
    label: 'Super Administrateur Démo',
    totpSecret: demoTotpSecret(),
  },
  ADMIN: {
    role: 'ADMIN',
    email: 'admin@example.test',
    password: DEMO_PASSWORD,
    roles: ['ADMIN'],
    label: 'Administrateur Démo',
    totpSecret: demoTotpSecret(),
  },
  TEACHER: {
    role: 'TEACHER',
    email: 'formateur@example.test',
    password: DEMO_PASSWORD,
    roles: ['TEACHER'],
    label: 'Formateur Démo',
  },
  STUDENT: {
    role: 'STUDENT',
    email: 'apprenant1@example.test',
    password: DEMO_PASSWORD,
    roles: ['STUDENT'],
    label: 'Alice Martin',
  },
  PEDAGOGICAL_MANAGER_TEACHER: {
    role: 'PEDAGOGICAL_MANAGER_TEACHER',
    email: 'responsable@example.test',
    password: DEMO_PASSWORD,
    roles: ['PEDAGOGICAL_MANAGER', 'TEACHER'],
    label: 'Responsable Pédagogique Démo',
  },
};

/** Second apprenant (utile pour les tests d'isolation d'un apprenant à l'autre, AC-017). */
export const STUDENT_TWO: DemoAccount = {
  role: 'STUDENT',
  email: 'apprenant2@example.test',
  password: DEMO_PASSWORD,
  roles: ['STUDENT'],
  label: 'Karim Diallo',
};

/**
 * Données de référence réellement présentes dans `esic_connect_demo`
 * (vérifiées par appel API direct avant l'écriture de la suite, jamais
 * inventées — cf. docs/09-strategie-tests.md).
 */
export const DEMO_DATA = {
  siteCode: 'SITE-DEMO',
  siteName: 'Campus démonstration',
  programCode: 'PRG-DEMO',
  classCode: 'C-DEMO',
  academicYearCode: 'AY-DEMO',
  sessionTitle: 'Atelier émargement (démo)',
  // `teacherPublicId` n'est volontairement PAS figé ici : `public_id` est
  // un `UUID.randomUUID()` généré au `@PrePersist`, donc régénéré à chaque
  // recréation de la base de démonstration. Utiliser
  // `demoTeacherPublicId(page.request)` (`tests/support/api.ts`).
};
