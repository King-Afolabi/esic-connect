/**
 * Fabrique un JWT **non signé** (signature factice) pour les tests.
 * Fichier utilitaire de test — jamais inclus dans le bundle applicatif
 * (`*.testing.ts` est exclu de `tsconfig.app.json`).
 */
export function makeJwt(payload: Record<string, unknown>): string {
  const b64url = (value: string) =>
    btoa(value)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64url(JSON.stringify(payload));
  return `${header}.${body}.signature-not-verified`;
}
