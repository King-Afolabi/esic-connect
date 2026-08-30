import { HttpErrorResponse } from '@angular/common/http';

import { fieldForAlternationCode, toAlternationError } from './alternation-errors';

function apiError(status: number, code: string, message: string, details: string[] = []) {
  return new HttpErrorResponse({
    status,
    statusText: 'x',
    error: { status, code, message, path: '/x', correlationId: null, details },
  });
}

describe('toAlternationError', () => {
  it('marks a 403 as forbidden and keeps the ALT code', () => {
    const view = toAlternationError(apiError(403, 'ALT_FORBIDDEN', 'Hors de votre périmètre.'));
    expect(view.forbidden).toBe(true);
    expect(view.notFound).toBe(false);
    expect(view.code).toBe('ALT_FORBIDDEN');
    expect(view.message).toContain('périmètre');
  });

  it('marks a 404 as notFound', () => {
    const view = toAlternationError(apiError(404, 'ALT_PATTERN_NOT_FOUND', 'Aucun modèle.'));
    expect(view.notFound).toBe(true);
  });

  it('appends non-sensitive configuration details to the message', () => {
    const view = toAlternationError(
      apiError(400, 'ALT_INVALID_CONFIGURATION', 'Configuration invalide.', [
        'propriété inconnue : foo',
      ]),
    );
    expect(view.code).toBe('ALT_INVALID_CONFIGURATION');
    expect(view.message).toContain('propriété inconnue : foo');
    expect(view.details).toEqual(['propriété inconnue : foo']);
  });

  it('does not surface a 5xx body message', () => {
    const view = toAlternationError(
      new HttpErrorResponse({ status: 500, statusText: 'x', error: 'stacktrace' }),
    );
    expect(view.code).toBeNull();
    expect(view.message).not.toContain('stacktrace');
  });
});

describe('fieldForAlternationCode', () => {
  it('binds known codes to a form field', () => {
    expect(fieldForAlternationCode('ALT_DUPLICATE_CODE')).toBe('code');
    expect(fieldForAlternationCode('ALT_INVALID_TIME_ZONE')).toBe('timeZoneId');
    expect(fieldForAlternationCode('ALT_INVALID_CONFIGURATION')).toBe('configuration');
  });

  it('returns null for an unmapped or missing code', () => {
    expect(fieldForAlternationCode('ALT_ASSIGNMENT_OVERLAP')).toBeNull();
    expect(fieldForAlternationCode(null)).toBeNull();
  });
});
