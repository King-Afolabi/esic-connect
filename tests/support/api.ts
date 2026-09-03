import { APIRequestContext } from '@playwright/test';
import { ACCOUNTS, DemoAccount } from './accounts';

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

/** Authentifie un compte de démonstration et renvoie l'en-tête `Authorization` complet. */
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
  const body = (await response.json()) as { accessToken: string };
  return `Bearer ${body.accessToken}`;
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
