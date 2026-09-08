import { buildRoomCheckInUrl, parseCheckInReference } from './check-in-reference';

const ORIGIN = 'https://esic.example';
const OPTS = { allowedOrigins: [ORIGIN, 'http://localhost:4200'] };

// Base64 URL-safe ~43 caractères — forme d'un jeton serveur / d'une réf. d'affiche.
const OPAQUE = 'q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy';

describe('parseCheckInReference', () => {
  it('treats a bare opaque string as a dynamic attendance token', () => {
    const parsed = parseCheckInReference(OPAQUE, OPTS);
    expect(parsed).toEqual({ kind: 'DYNAMIC_ATTENDANCE_TOKEN', token: OPAQUE });
  });

  it('treats a bare opaque string as a room reference only when explicitly asked', () => {
    const parsed = parseCheckInReference(OPAQUE, { ...OPTS, bareStringIsRoomReference: true });
    expect(parsed).toEqual({ kind: 'STATIC_ROOM_REFERENCE', roomReference: OPAQUE });
  });

  it('extracts the room reference from an internal check-in URL', () => {
    const parsed = parseCheckInReference(`${ORIGIN}/attendance?ref=${OPAQUE}`, OPTS);
    expect(parsed).toEqual({ kind: 'STATIC_ROOM_REFERENCE', roomReference: OPAQUE });
  });

  it('accepts a trailing slash on the path', () => {
    const parsed = parseCheckInReference(`${ORIGIN}/attendance/?ref=${OPAQUE}`, OPTS);
    expect(parsed.kind).toBe('STATIC_ROOM_REFERENCE');
  });

  it('rejects an external URL, even one pointing at /attendance', () => {
    expect(parseCheckInReference(`https://evil.example/attendance?ref=${OPAQUE}`, OPTS).kind).toBe(
      'UNSUPPORTED',
    );
  });

  it('rejects an internal URL on another path', () => {
    expect(parseCheckInReference(`${ORIGIN}/dashboard?ref=${OPAQUE}`, OPTS).kind).toBe('UNSUPPORTED');
  });

  it('rejects an internal check-in URL whose ref is not opaque', () => {
    expect(parseCheckInReference(`${ORIGIN}/attendance?ref=short`, OPTS).kind).toBe('UNSUPPORTED');
    expect(parseCheckInReference(`${ORIGIN}/attendance?ref=a%20b%20c%20d%20e%20f%20g`, OPTS).kind).toBe(
      'UNSUPPORTED',
    );
  });

  it('rejects a QR from another application / free text', () => {
    expect(parseCheckInReference('WIFI:S:home;T:WPA;P:secret;;', OPTS).kind).toBe('UNSUPPORTED');
    expect(parseCheckInReference('hello world', OPTS).kind).toBe('UNSUPPORTED');
    expect(parseCheckInReference('', OPTS).kind).toBe('UNSUPPORTED');
  });

  it('rejects an over-long or wrongly-charactered opaque string', () => {
    expect(parseCheckInReference('x'.repeat(200), OPTS).kind).toBe('UNSUPPORTED');
    expect(parseCheckInReference('abc+def/ghi=jkl mno pqr', OPTS).kind).toBe('UNSUPPORTED');
  });

  it('trims surrounding whitespace before deciding', () => {
    expect(parseCheckInReference(`  ${OPAQUE}  `, OPTS).kind).toBe('DYNAMIC_ATTENDANCE_TOKEN');
  });
});

describe('buildRoomCheckInUrl', () => {
  it('joins the configured origin and the backend check-in path', () => {
    expect(buildRoomCheckInUrl('/attendance?ref=' + OPAQUE, ORIGIN)).toBe(
      `${ORIGIN}/attendance?ref=${OPAQUE}`,
    );
  });

  it('tolerates a trailing slash on the origin', () => {
    expect(buildRoomCheckInUrl('/attendance?ref=' + OPAQUE, ORIGIN + '/')).toBe(
      `${ORIGIN}/attendance?ref=${OPAQUE}`,
    );
  });

  it('returns null when the path is missing, relative-less or lacks a ref', () => {
    expect(buildRoomCheckInUrl(null, ORIGIN)).toBeNull();
    expect(buildRoomCheckInUrl('attendance?ref=x', ORIGIN)).toBeNull();
    expect(buildRoomCheckInUrl('/attendance', ORIGIN)).toBeNull();
  });

  it('returns null when the origin is not http(s)', () => {
    expect(buildRoomCheckInUrl('/attendance?ref=' + OPAQUE, 'ftp://x')).toBeNull();
    expect(buildRoomCheckInUrl('/attendance?ref=' + OPAQUE, 'esic.example')).toBeNull();
  });
});
