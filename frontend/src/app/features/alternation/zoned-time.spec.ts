import { COMMON_TIME_ZONES, isSupportedTimeZone, zonedWallTimeToInstant } from './zoned-time';

describe('isSupportedTimeZone', () => {
  it('accepts a valid IANA identifier and rejects an unknown one', () => {
    expect(isSupportedTimeZone('Europe/Paris')).toBe(true);
    expect(isSupportedTimeZone('UTC')).toBe(true);
    expect(isSupportedTimeZone('Mars/Olympus')).toBe(false);
    expect(isSupportedTimeZone('')).toBe(false);
  });

  it('offers Europe/Paris and UTC in the curated list', () => {
    expect(COMMON_TIME_ZONES).toContain('Europe/Paris');
    expect(COMMON_TIME_ZONES).toContain('UTC');
  });
});

describe('zonedWallTimeToInstant', () => {
  it('encodes a winter wall time in Europe/Paris (UTC+1)', () => {
    expect(zonedWallTimeToInstant('2026-01-15T09:00', 'Europe/Paris')).toBe(
      '2026-01-15T08:00:00.000Z',
    );
  });

  it('encodes a summer wall time in Europe/Paris (UTC+2, DST)', () => {
    expect(zonedWallTimeToInstant('2026-07-15T09:00', 'Europe/Paris')).toBe(
      '2026-07-15T07:00:00.000Z',
    );
  });

  it('treats a UTC wall time as-is', () => {
    expect(zonedWallTimeToInstant('2026-09-07T06:30', 'UTC')).toBe('2026-09-07T06:30:00.000Z');
  });

  it('returns null for an unknown zone (never falls back to UTC)', () => {
    expect(zonedWallTimeToInstant('2026-09-07T06:30', 'Mars/Olympus')).toBeNull();
  });

  it('returns null for a malformed wall time', () => {
    expect(zonedWallTimeToInstant('not-a-date', 'Europe/Paris')).toBeNull();
    expect(zonedWallTimeToInstant('', 'Europe/Paris')).toBeNull();
  });

  it('does not shift the chosen zone: the same wall time in two zones yields different instants', () => {
    const paris = zonedWallTimeToInstant('2026-03-10T12:00', 'Europe/Paris');
    const utc = zonedWallTimeToInstant('2026-03-10T12:00', 'UTC');
    expect(paris).not.toBe(utc);
  });
});
