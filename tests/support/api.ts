import { APIRequestContext } from '@playwright/test';
import { ACCOUNTS, DemoAccount } from './accounts';
import { claimTotpCode } from './totp';

/**
 * Accès API direct, utilisé UNIQUEMENT pour résoudre des identifiants
 * techniques que le dépôt ne peut pas figer.
 *
 * Motif : `public_id` est un `UUID.randomUUID()` généré au `@PrePersist`
 * (`backend/.../shared/BaseEntity.java`). Il change donc à chaque
 * recréation de la base de démonstration. Un identifiant écrit en dur dans
 * le dépôt casse la suite dès le premier `scripts/db-reset.sh`.
 */
export const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080';

/**
 * Authentifie un compte de démonstration et renvoie l'en-tête
 * `Authorization` complet.
 *
 * <p>`ADMIN` et `SUPER_ADMIN` exigent un second facteur (RG-007) : la
 * connexion ne renvoie jamais `accessToken` directement pour eux — un
 * appel API direct (hors navigateur) doit donc franchir le VRAI défi
 * `/mfa/verify` ou `/mfa/enroll` + `/mfa/enroll/confirm`, comme le fait
 * `loginAsUi` côté UI (`tests/support/auth.ts`, dette T-19/T-20). Sans
 * cela, `body.accessToken` est `undefined` et l'appel suivant échoue en
 * `401` avec un en-tête `Bearer undefined` — défaut réellement observé
 * ici avant cette correction.
 */
export async function apiLogin(ctx: APIRequestContext, account: DemoAccount): Promise<string> {
  const response = await ctx.post(`${API_BASE}/api/v1/auth/login`, {
    data: { email: account.email, password: account.password },
  });
  if (!response.ok()) {
    throw new Error(
      `Connexion API impossible pour ${account.email} (HTTP ${response.status()}). ` +
        'Le back-end est-il démarré en profil `demo` avec le même ESIC_DEMO_PASSWORD ?',
    );
  }
  const body = (await response.json()) as {
    accessToken?: string;
    mfa?: { challengeId: string; purpose: 'VERIFY' | 'ENROLL' };
  };
  if (body.accessToken) {
    return `Bearer ${body.accessToken}`;
  }
  if (!body.mfa) {
    throw new Error(`Connexion API pour ${account.email} : ni accessToken ni défi MFA dans la réponse.`);
  }
  return resolveApiMfaChallenge(ctx, account, body.mfa.challengeId, body.mfa.purpose);
}

/** Franchit le défi MFA d'une connexion API directe — voir {@link apiLogin}. */
async function resolveApiMfaChallenge(
  ctx: APIRequestContext,
  account: DemoAccount,
  challengeId: string,
  purpose: 'VERIFY' | 'ENROLL',
): Promise<string> {
  if (purpose === 'VERIFY') {
    if (!account.totpSecret) {
      throw new Error(
        `Second facteur requis pour ${account.email} (appel API direct) mais aucun secret TOTP ` +
          'disponible : définissez ESIC_DEMO_TOTP_SECRET. Voir docs/STATUS.md, dette T-19/T-20.',
      );
    }
    for (let attempt = 1; attempt <= 2; attempt += 1) {
      const code = await claimTotpCode(account.totpSecret, (ms) => new Promise((r) => setTimeout(r, ms)));
      const response = await ctx.post(`${API_BASE}/api/v1/auth/mfa/verify`, {
        data: { challengeId, code },
      });
      if (response.ok()) {
        const body = (await response.json()) as { accessToken: string };
        return `Bearer ${body.accessToken}`;
      }
      if (attempt === 2) {
        throw new Error(`Échec de /mfa/verify pour ${account.email} (HTTP ${response.status()}).`);
      }
    }
  }

  // ENROLL : secret aléatoire généré par le serveur, jamais celui de
  // l'environnement — voir tests/support/auth.ts pour le même parcours côté UI.
  const enrollResponse = await ctx.post(`${API_BASE}/api/v1/auth/mfa/enroll`, {
    data: { challengeId },
  });
  if (!enrollResponse.ok()) {
    throw new Error(`Échec de /mfa/enroll pour ${account.email} (HTTP ${enrollResponse.status()}).`);
  }
  const { secret } = (await enrollResponse.json()) as { secret: string };
  const code = await claimTotpCode(secret, (ms) => new Promise((r) => setTimeout(r, ms)));
  const confirmResponse = await ctx.post(`${API_BASE}/api/v1/auth/mfa/enroll/confirm`, {
    data: { challengeId, code },
  });
  if (!confirmResponse.ok()) {
    throw new Error(`Échec de /mfa/enroll/confirm pour ${account.email} (HTTP ${confirmResponse.status()}).`);
  }
  const { session } = (await confirmResponse.json()) as { session: { accessToken: string } };
  return `Bearer ${session.accessToken}`;
}

/** Résout le `publicId` d'un compte à partir de son adresse électronique. */
export async function resolveUserPublicId(
  ctx: APIRequestContext,
  authorization: string,
  email: string,
): Promise<string> {
  const response = await ctx.get(`${API_BASE}/api/v1/users`, {
    params: { q: email },
    headers: { Authorization: authorization },
  });
  if (!response.ok()) {
    throw new Error(`GET /api/v1/users?q=${email} a répondu ${response.status()}`);
  }
  const body = (await response.json()) as { content?: Array<{ email: string; publicId: string }> };
  const match = body.content?.find((u) => u.email === email);
  if (!match) {
    throw new Error(
      `Compte ${email} introuvable. La base de démonstration est-elle amorcée (profil \`demo\`) ?`,
    );
  }
  return match.publicId;
}

let cachedTeacherPublicId: string | undefined;

/**
 * `publicId` du formateur de démonstration (`formateur@example.test`),
 * résolu une seule fois par exécution puis mémorisé.
 */
export async function demoTeacherPublicId(ctx: APIRequestContext): Promise<string> {
  if (!cachedTeacherPublicId) {
    const authorization = await apiLogin(ctx, ACCOUNTS.ADMIN);
    cachedTeacherPublicId = await resolveUserPublicId(ctx, authorization, ACCOUNTS.TEACHER.email);
  }
  return cachedTeacherPublicId;
}
