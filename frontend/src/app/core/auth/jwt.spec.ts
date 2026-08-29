import { decodeJwtPayload, readExpiry, readRoles, readSubject } from './jwt';

/** Fabrique un JWT non signé (signature factice) pour les tests. */
export function makeJwt(payload: Record<string, unknown>): string {
  const b64url = (value: string) =>
    btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64url(JSON.stringify(payload));
  return `${header}.${body}.signature-not-verified`;
}

describe('jwt helpers', () => {
  it('decodes a base64url payload', () => {
    const token = makeJwt({ sub: 'public-id-1', roles: ['ADMIN'] });
    expect(decodeJwtPayload(token)).toEqual({ sub: 'public-id-1', roles: ['ADMIN'] });
  });

  it('returns null for a malformed token', () => {
    expect(decodeJwtPayload('not-a-jwt')).toBeNull();
    expect(decodeJwtPayload('a.b')).toBeNull();
  });

  it('reads the subject claim', () => {
    expect(readSubject(makeJwt({ sub: 'abc' }))).toBe('abc');
    expect(readSubject(makeJwt({}))).toBeNull();
  });

  it('keeps only known role codes', () => {
    const token = makeJwt({ roles: ['ADMIN', 'TEACHER', 'MARTIAN', 42] });
    expect(readRoles(token)).toEqual(['ADMIN', 'TEACHER']);
  });

  it('returns an empty role list when the claim is missing or not an array', () => {
    expect(readRoles(makeJwt({}))).toEqual([]);
    expect(readRoles(makeJwt({ roles: 'ADMIN' }))).toEqual([]);
  });

  it('converts the exp claim (seconds) to milliseconds', () => {
    const token = makeJwt({ exp: 1_900_000_000 });
    expect(readExpiry(token, 900)).toBe(1_900_000_000_000);
  });

  it('falls back to now + ttl when exp is absent', () => {
    const now = 1_000_000;
    expect(readExpiry(makeJwt({}), 900, now)).toBe(now + 900_000);
  });
});
